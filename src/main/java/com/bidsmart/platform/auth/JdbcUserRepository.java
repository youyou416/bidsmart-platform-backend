package com.bidsmart.platform.auth;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcUserRepository implements UserRepository {
    private static final String SELECT = "SELECT u.id,u.username,u.display_name,u.dept_id,d.name AS dept_name,"
            + "u.identity_type,u.enterprise_id,u.data_scope_type,u.password_hash,u.status,"
            + "u.password_change_required,u.password_version FROM sys_user u "
            + "LEFT JOIN sys_dept d ON d.id=u.dept_id ";
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public JdbcUserRepository(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc; this.clock = clock;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return first(jdbc.query(SELECT + "WHERE u.username=:username",
                Collections.singletonMap("username", username), (rs, row) -> map(rs)));
    }

    @Override
    public Optional<UserAccount> findById(long id) {
        return first(jdbc.query(SELECT + "WHERE u.id=:id",
                Collections.singletonMap("id", id), (rs, row) -> map(rs)));
    }

    @Override
    @Transactional
    public boolean changePassword(long id, long expectedVersion, String passwordHash) {
        MapSqlParameterSource values = new MapSqlParameterSource("id", id)
                .addValue("version", expectedVersion).addValue("hash", passwordHash)
                .addValue("time", Timestamp.from(clock.instant()));
        return jdbc.update("UPDATE sys_user SET password_hash=:hash,password_change_required=FALSE,"
                + "password_version=password_version+1,updated_at=:time "
                + "WHERE id=:id AND password_version=:version AND status='ENABLED'", values) == 1;
    }

    @Override
    @Transactional
    public boolean createBootstrap(String username, String passwordHash, long roleId, long departmentId) {
        if (findByUsername(username).isPresent()) { return false; }
        Integer roles = jdbc.queryForObject("SELECT COUNT(*) FROM sys_role WHERE id=:id AND status='ENABLED'",
                Collections.singletonMap("id", roleId), Integer.class);
        Integer departments = jdbc.queryForObject("SELECT COUNT(*) FROM sys_dept WHERE id=:id AND status='ENABLED'",
                Collections.singletonMap("id", departmentId), Integer.class);
        if (roles == null || roles != 1 || departments == null || departments != 1) {
            throw new IllegalStateException("Bootstrap requires the selected enabled role and department");
        }
        long id;
        do { id = random.nextLong() & Long.MAX_VALUE; } while (id == 0);
        MapSqlParameterSource values = new MapSqlParameterSource("id", id)
                .addValue("username", username).addValue("hash", passwordHash)
                .addValue("department", departmentId).addValue("role", roleId)
                .addValue("time", Timestamp.from(clock.instant()));
        jdbc.update("INSERT INTO sys_user (id,username,display_name,dept_id,identity_type,enterprise_id,"
                + "data_scope_type,password_hash,status,password_change_required,password_version,created_at,updated_at) "
                + "VALUES (:id,:username,'本地初始化账户',:department,'BUYER',NULL,'SELF_AND_SPECIFIED',"
                + ":hash,'ENABLED',TRUE,1,:time,:time)", values);
        jdbc.update("INSERT INTO sys_user_role (user_id,role_id) VALUES (:id,:role)", values);
        return true;
    }

    private Optional<UserAccount> first(List<UserAccount> values) {
        if (values.size() > 1) { throw new IllegalStateException("Account uniqueness constraint is missing"); }
        return values.stream().findFirst();
    }

    private UserAccount map(ResultSet rs) throws SQLException {
        Long dept = rs.getObject("dept_id") == null ? null : rs.getLong("dept_id");
        Long enterprise = rs.getObject("enterprise_id") == null ? null : rs.getLong("enterprise_id");
        return new UserAccount(rs.getLong("id"), rs.getString("username"), rs.getString("display_name"),
                dept, rs.getString("dept_name"), rs.getString("identity_type"), enterprise,
                rs.getString("data_scope_type"), rs.getString("password_hash"),
                "ENABLED".equals(rs.getString("status")), rs.getBoolean("password_change_required"),
                rs.getLong("password_version"));
    }
}
