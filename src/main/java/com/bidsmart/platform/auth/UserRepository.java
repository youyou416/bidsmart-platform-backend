package com.bidsmart.platform.auth;

import java.util.Optional;

public interface UserRepository {
    Optional<UserAccount> findByUsername(String username);
    Optional<UserAccount> findById(long id);
    boolean changePassword(long id, long expectedVersion, String passwordHash);
    boolean createBootstrap(String username, String passwordHash, long roleId, long departmentId);
}
