package com.example.login;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbc;
    public UserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Account(long id, String username, String passwordHash,
                          String nickname, String email, String department) {}

    public Optional<Account> findByUsername(String username) {
        return jdbc.query("SELECT * FROM app_user WHERE username = ?", (rs, row) ->
            new Account(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"),
                rs.getString("nickname"), rs.getString("email"), rs.getString("department")), username)
            .stream().findFirst();
    }

    public void createIfAbsent(String username, String hash, String nickname, String email, String department) {
        if (findByUsername(username).isEmpty()) {
            jdbc.update("INSERT INTO app_user(username,password_hash,nickname,email,department) VALUES (?,?,?,?,?)",
                username, hash, nickname, email, department);
        }
    }
}
