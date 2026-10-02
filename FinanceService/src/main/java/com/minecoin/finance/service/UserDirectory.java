package com.minecoin.finance.service;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface UserDirectory {

    Optional<UserRef> findByUsername(String username);

    Map<UUID, String> findUsernames(Collection<UUID> userIds);
}
