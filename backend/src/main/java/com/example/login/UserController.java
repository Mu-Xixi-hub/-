package com.example.login;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class UserController {
    private final UserRepository users;
    public UserController(UserRepository users) { this.users = users; }

    @GetMapping("/api/csrf")
    public Map<String, String> csrf(CsrfToken csrf) {
        return Map.of("token", csrf.getToken(), "headerName", csrf.getHeaderName());
    }

    public record UserInfo(long id, String username, String nickname, String email, String department) {}

    @GetMapping("/api/me")
    public UserInfo me(Principal principal) {
        // 只信任服务端会话中的身份，不接收前端传来的用户 ID。
        var user = users.findByUsername(principal.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new UserInfo(user.id(), user.username(), user.nickname(), user.email(), user.department());
    }
}
