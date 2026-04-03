package com.acme.demo.service;

import com.acme.demo.client.UserApiClient;
import com.acme.demo.dto.CreateUserRequest;
import com.acme.demo.dto.UserDto;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Application service that delegates to {@link UserApiClient}.
 * <p>
 * In a real application you would add business logic, mapping, caching etc. here.
 * Resilience4j annotations ({@code @CircuitBreaker}, {@code @Retry}) can also be
 * applied at this layer instead of—or in addition to—the starter-level configuration.
 */
@Service
public class UserService {

    private final UserApiClient userApiClient;

    public UserService(UserApiClient userApiClient) {
        this.userApiClient = userApiClient;
    }

    public Mono<UserDto> getUser(String id) {
        return userApiClient.getUser(id, null);
    }

    public Flux<UserDto> listUsers(String role) {
        return userApiClient.listUsers(role);
    }

    public Mono<UserDto> createUser(String name, String email, String tenant) {
        return userApiClient.createUser(new CreateUserRequest(name, email), tenant);
    }

    public Mono<UserDto> updateUser(String id, UserDto updated) {
        return userApiClient.updateUser(id, updated);
    }

    public Mono<Void> deleteUser(String id) {
        return userApiClient.deleteUser(id);
    }
}
