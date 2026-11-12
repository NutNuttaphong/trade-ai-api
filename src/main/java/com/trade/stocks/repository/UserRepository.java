package com.trade.stocks.repository;

import com.trade.stocks.entity.User;
import org.springframework.data.repository.ListCrudRepository;
import java.util.Optional;

public interface UserRepository extends ListCrudRepository<User, Long> {
    Optional<User> findByUsername(String username);
}
