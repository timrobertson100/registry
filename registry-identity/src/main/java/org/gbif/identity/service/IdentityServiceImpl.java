package org.gbif.identity.service;

import org.gbif.api.model.common.GbifUser;
import org.gbif.api.model.common.paging.Pageable;
import org.gbif.api.model.common.paging.PagingRequest;
import org.gbif.api.model.common.paging.PagingResponse;
import org.gbif.api.model.registry.PostPersist;
import org.gbif.api.model.registry.PrePersist;
import org.gbif.api.service.common.IdentityService;
import org.gbif.identity.model.ModelMutationError;
import org.gbif.identity.model.PropertyConstants;
import org.gbif.identity.model.UserModelMutationResult;
import org.gbif.identity.mybatis.UserMapper;
import org.gbif.identity.util.PasswordEncoder;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.groups.Default;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import org.apache.bval.jsr303.ApacheValidationProvider;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.guice.transactional.Transactional;

import static org.gbif.identity.model.UserModelMutationResult.withError;
import static org.gbif.identity.model.UserModelMutationResult.withSingleConstraintViolation;


/**
 * Main implementation of {@link IdentityService} on top of mybatis.
 * Notes:
 *  - usernames are case insensitive but the constraint in the db only allows lowercase at the moment (could be removed)
 *  - emails are stored and queried in lowercase
 */
class IdentityServiceImpl implements IdentityService {

  private final UserMapper userMapper;
  private final UserSuretyDelegateIf userSuretyService;

  private final Range<Integer> PASSWORD_LENGTH_RANGE = Range.between(6, 256);

  private static final Validator BEAN_VALIDATOR =
          Validation.byProvider(ApacheValidationProvider.class)
                  .configure()
                  .buildValidatorFactory()
                  .getValidator();

  private static final Function<String, String> NORMALIZE_USERNAME_FCT = StringUtils::trim;
  private static final Function<String, String> NORMALIZE_EMAIL_FCT = (email) -> email == null ? null : email.trim().toLowerCase();

  private static final PasswordEncoder encoder = new PasswordEncoder();

  @Inject
  IdentityServiceImpl(UserMapper userMapper,
                      UserSuretyDelegateIf userSuretyService) {
    this.userMapper = userMapper;
    this.userSuretyService = userSuretyService;
  }

  @Override
  @Transactional
  public UserModelMutationResult create(GbifUser rawUser, String password) {

    GbifUser user = normalize(rawUser);
    if (userMapper.get(user.getUserName()) != null ||
            userMapper.getByEmail(user.getEmail()) != null) {
      return withError(ModelMutationError.USER_ALREADY_EXIST);
    }

    if(StringUtils.isBlank(password) || !PASSWORD_LENGTH_RANGE.contains(password.length())) {
      return withError(ModelMutationError.PASSWORD_LENGTH_VIOLATION);
    }

    user.setPasswordHash(encoder.encode(password));

    Set<ConstraintViolation<GbifUser>> violations = BEAN_VALIDATOR.validate(user,
            PrePersist.class, Default.class);
    if(!violations.isEmpty()) {
      return withError(violations);
    }
    userMapper.create(user);

    //trigger email
    userSuretyService.onNewUser(user);

    return UserModelMutationResult.onSuccess(user.getUserName(), user.getEmail());
  }

  @Override
  public UserModelMutationResult update(GbifUser rawUser) {
    GbifUser user = normalize(rawUser);
    GbifUser currentUser = getByKey(user.getKey());
    if (currentUser != null) {

      //handle email change and user if the user want to change it is is not already
      //linked to another account
      if (!currentUser.getEmail().equalsIgnoreCase(user.getEmail())) {
        GbifUser currentUserWithEmail = userMapper.getByEmail(user.getEmail());
        if (currentUserWithEmail != null) {
          return UserModelMutationResult.withError(ModelMutationError.EMAIL_ALREADY_IN_USE);
        }
      }

      Set<ConstraintViolation<GbifUser>> violations = BEAN_VALIDATOR.validate(user,
              PostPersist.class, Default.class);
      if (!violations.isEmpty()) {
        return UserModelMutationResult.withError(violations);
      }
      userMapper.update(user);
    } else {
      //means the user doesn't exist
      return null;
    }
    return UserModelMutationResult.onSuccess(user.getUserName(), user.getEmail());
  }

  @Override
  public void delete(int userKey) {
    userMapper.delete(userKey);
  }

  @Override
  public GbifUser getByKey(int key) {
    return userMapper.getByKey(key);
  }

  /**
   * Get a {@link GbifUser} using its username.
   * The username is case insensitive.
   * @param username
   * @return {@link GbifUser} or null
   */
  @Override
  public GbifUser get(String username) {
    if (Strings.isNullOrEmpty(username)) {
      return null;
    }
    // the mybatis mapper will run the query with a lower()
    return userMapper.get(NORMALIZE_USERNAME_FCT.apply(username));
  }

  /**
   * Get a {@link GbifUser} using its email.
   * The email is case insensitive.
   * @param email
   * @return {@link GbifUser} or null
   */
  @Override
  public GbifUser getByEmail(String email) {
    // emails are stored in lowercase
    return userMapper.getByEmail(NORMALIZE_EMAIL_FCT.apply(email));
  }

  @Override
  public PagingResponse<GbifUser> list(@Nullable Pageable pageable) {
    return search(null, pageable);
  }

  @Override
  public PagingResponse<GbifUser> search(@Nullable String query, @Nullable Pageable pageable) {
    return pagingResponse(pageable, userMapper.count(query), userMapper.search(query, pageable));
  }

  /**
   * Authenticate a user
   * @param username username or email address
   * @param password clear text password
   *
   * @return
   */
  @Override
  public GbifUser authenticate(String username, String password) {
    if (Strings.isNullOrEmpty(username) || password == null) {
      return null;
    }

    GbifUser u = getByIdentifier(username);
    if (u != null) {
      // use the settings which are the prefix in the existing password hash to encode the provided password
      // and verify that they result in the same
      String phash = encoder.encode(password, u.getPasswordHash());
      if (phash.equalsIgnoreCase(u.getPasswordHash())) {

        //ensure there is no pending challenge code, unless the user already logged in in the past.
        //If the user logged in in the past we assume the challengeCode is from a reset password and since we
        //can't be sure the user initiated the request himself we must allow to login
        if(!userSuretyService.hasChallengeCode(u.getKey()) || u.getLastLogin() != null) {
          return u;
        }
      }
    }
    return null;
  }

  @Override
  public GbifUser getByIdentifier(String identifier) {
    //this assumes username name can not contains @ (see User's getUserName())
    return StringUtils.contains(identifier, "@") ?
            getByEmail(identifier) :get(identifier);
  }

  @Override
  public void updateLastLogin(int userKey){
    userMapper.updateLastLogin(userKey);
  }

  @Override
  public boolean hasPendingConfirmation(int userKey) {
    return userSuretyService.hasChallengeCode(userKey);
  }

  @Override
  public boolean isConfirmationKeyValid(int userKey, UUID confirmationKey) {
    return userSuretyService.isValidChallengeCode(userKey, confirmationKey);
  }

  @Override
  public boolean confirmUser(int userKey, UUID confirmationKey) {
    if (confirmationKey != null) {
        return userSuretyService.confirmUser(userKey, confirmationKey);
    }
    return false;
  }

  @Override
  public void resetPassword(int userKey) {
    // ensure the user exists
    GbifUser user = userMapper.getByKey(userKey);
    if (user != null) {
      userSuretyService.onPasswordReset(user);
    }
  }

  @Override
  public UserModelMutationResult updatePassword(int userKey, String newPassword, UUID challengeCode) {
    if(confirmUser(userKey, challengeCode)){
      return updatePassword(userKey, newPassword);
    }
    return withSingleConstraintViolation(PropertyConstants.CHALLENGE_CODE_PROPERTY_NAME, PropertyConstants.CONSTRAINT_INCORRECT);
  }

  @Override
  public UserModelMutationResult updatePassword(int userKey, String newPassword) {
    GbifUser user = userMapper.getByKey(userKey);
    if(user != null){
      if(StringUtils.isBlank(newPassword) || !PASSWORD_LENGTH_RANGE.contains(newPassword.length())) {
        return withError(ModelMutationError.PASSWORD_LENGTH_VIOLATION);
      }
      user.setPasswordHash(encoder.encode(newPassword));
      userMapper.update(user);
    }
    else{
      return withSingleConstraintViolation("user", PropertyConstants.CONSTRAINT_UNKNOWN);
    }
    return UserModelMutationResult.onSuccess();
  }

  /**
   * The main purpose of this method is to normalize the content of some fields from a {@link GbifUser}.
   * The goal is to ensure we can query this object in the same way we handle inserts/updates.
   *  - trim() on username
   *  - trim() + toLowerCase() on emails
   * @param gbifUser
   * @return
   */
  private static GbifUser normalize(GbifUser gbifUser){
    gbifUser.setUserName(NORMALIZE_USERNAME_FCT.apply(gbifUser.getUserName()));
    gbifUser.setEmail(NORMALIZE_EMAIL_FCT.apply(gbifUser.getEmail()));
    return gbifUser;
  }

  /**
   * Null safe builder to construct a paging response.
   *
   * @param page page to create response for, can be null
   */
  private static PagingResponse<GbifUser> pagingResponse(@Nullable Pageable page, long count, List<GbifUser> result) {
    return new PagingResponse<>(page == null ? new PagingRequest() : page, count, result);
  }

}
