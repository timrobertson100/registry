package org.gbif.registry.persistence.mapper.handler;

import org.gbif.api.vocabulary.collections.Discipline;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.google.common.base.Strings;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/** {@link org.apache.ibatis.type.TypeHandler} for arrays of {@link Discipline}. */
public class DisciplineArrayTypeHandler extends BaseTypeHandler<List<Discipline>> {

  @Override
  public void setNonNullParameter(
      PreparedStatement ps, int i, List<Discipline> parameter, JdbcType jdbcType)
      throws SQLException {
    Array array = ps.getConnection().createArrayOf("text", parameter.toArray());
    ps.setArray(i, array);
  }

  @Override
  public List<Discipline> getNullableResult(ResultSet rs, String columnName) throws SQLException {
    return toList(rs.getArray(columnName));
  }

  @Override
  public List<Discipline> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
    return toList(rs.getArray(columnIndex));
  }

  @Override
  public List<Discipline> getNullableResult(CallableStatement cs, int columnIndex)
      throws SQLException {
    return toList(cs.getArray(columnIndex));
  }

  private List<Discipline> toList(Array pgArray) throws SQLException {
    if (pgArray == null) return new ArrayList<>();

    String[] strings = (String[]) pgArray.getArray();
    if (strings != null && strings.length > 0) {
      return Arrays.stream(strings)
          .filter(v -> !Strings.isNullOrEmpty(v))
          .map(Discipline::valueOf)
          .collect(Collectors.toList());
    }
    return new ArrayList<>();
  }
}
