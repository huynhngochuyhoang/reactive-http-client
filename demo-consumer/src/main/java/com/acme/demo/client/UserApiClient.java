package com.acme.demo.client;

import com.acme.demo.dto.CreateUserRequest;
import com.acme.demo.dto.UserDto;
import com.acme.httpstarter.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Declarative reactive HTTP client for the upstream {@code user-service}.
 *
 * <p>Configured via {@code acme.http.clients.user-service.*} in {@code application.yml}.
 */
@ReactiveHttpClient(name = "user-service")
public interface UserApiClient {

    /**
     * Retrieves a single user by ID.
     *
     * @param id     user identifier (path variable)
     * @param expand optional comma-separated list of fields to expand (query parameter)
     */
    @GET("/users/{id}")
    @ApiName("user.getById")
    Mono<UserDto> getUser(
            @PathVar("id") String id,
            @QueryParam("expand") String expand
    );

    /**
     * Lists all users (optionally filtered by role).
     */
    @GET("/users")
    Flux<UserDto> listUsers(@QueryParam("role") String role);

    /**
     * Searches users by multiple IDs supplied as a multi-value query parameter.
     * Produces: {@code GET /users/search?ids=1&ids=2&ids=3}
     */
    @GET("/users/search")
    Flux<UserDto> searchByIds(@QueryParam("ids") java.util.List<String> ids);

    /**
     * Creates a new user.
     *
     * @param request request body
     * @param tenant  tenant identifier forwarded as a request header
     */
    @POST("/users")
    Mono<UserDto> createUser(
            @Body CreateUserRequest request,
            @HeaderParam("X-Tenant") String tenant
    );

    /**
     * Updates an existing user.
     */
    @PUT("/users/{id}")
    Mono<UserDto> updateUser(
            @PathVar("id") String id,
            @Body UserDto body
    );

    /**
     * Deletes a user.
     */
    @DELETE("/users/{id}")
    Mono<Void> deleteUser(@PathVar("id") String id);
}
