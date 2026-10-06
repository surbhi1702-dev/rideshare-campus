package com.rideshare.integration;

import com.rideshare.support.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** httpOnly refresh cookie: issue, rotate, reuse detection, logout. */
class SessionIntegrationTest extends AbstractIntegrationTest {

    private static final String COOKIE = "rs_refresh";

    @Test
    void loginSetsAnHttpOnlyRefreshCookieScopedToAuthEndpoints() throws Exception {
        registerStudent("aarav");
        MvcResult login = login("aarav");

        String setCookie = login.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains(COOKIE + "=").contains("HttpOnly").contains("Path=/api/auth")
                .contains("SameSite=Lax");
        assertThat(read(login).get("accessToken").asText()).isNotBlank();
    }

    @Test
    void refreshReturnsAWorkingAccessTokenAndRotatesTheCookie() throws Exception {
        registerStudent("aarav");
        Cookie original = refreshCookie(login("aarav"));

        MvcResult refreshed = refresh(original).andExpect(status().isOk()).andReturn();
        Cookie rotated = refreshCookie(refreshed);
        assertThat(rotated.getValue()).isNotEqualTo(original.getValue());

        String bearer = "Bearer " + read(refreshed).get("accessToken").asText();
        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("aarav@iitbbs.ac.in"));
        refresh(rotated).andExpect(status().isOk());
    }

    @Test
    void tokenReusedRightAfterRotationIsRejectedButTheSessionSurvives() throws Exception {
        registerStudent("aarav");
        Cookie original = refreshCookie(login("aarav"));
        Cookie rotated = refreshCookie(refresh(original).andReturn());

        // Two tabs refreshed at the same moment with the same cookie.
        refresh(original).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        refresh(rotated).andExpect(status().isOk());
    }

    @Test
    void oldTokenComingBackLaterRevokesTheWholeSession() throws Exception {
        registerStudent("aarav");
        Cookie stolen = refreshCookie(login("aarav"));
        Cookie current = refreshCookie(refresh(stolen).andReturn());
        // Pretend the rotation happened a while ago (outside the grace period).
        jdbcTemplate.update("update refresh_tokens set revoked_at = revoked_at - interval '1 hour' "
                + "where revoked_reason = 'ROTATED'");

        refresh(stolen).andExpect(status().isUnauthorized());
        // The legitimate user's newer token is dead too: both must log in again.
        refresh(current).andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from refresh_tokens where revoked_reason = 'REUSE_DETECTED'", Long.class)).isEqualTo(1);
    }

    @Test
    void logoutClearsTheCookieAndEndsTheSession() throws Exception {
        registerStudent("aarav");
        Cookie cookie = refreshCookie(login("aarav"));

        MvcResult logout = mockMvc.perform(post("/api/auth/logout").cookie(cookie)
                        .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(logout.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        refresh(cookie).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCookieOrWithoutTheCsrfHeaderIsRejected() throws Exception {
        registerStudent("aarav");
        Cookie cookie = refreshCookie(login("aarav"));

        mockMvc.perform(post("/api/auth/refresh").header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/refresh").cookie(cookie))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatedUserCannotRefresh() throws Exception {
        TestUser student = registerStudent("aarav");
        Cookie cookie = refreshCookie(login("aarav"));
        jdbcTemplate.update("update users set active = false where id = ?", student.id());

        refresh(cookie).andExpect(status().isUnauthorized());
    }

    private MvcResult login(String handle) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", handle + "@iitbbs.ac.in", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
    }

    private ResultActions refresh(Cookie cookie) throws Exception {
        return mockMvc.perform(post("/api/auth/refresh").cookie(cookie).header("X-Requested-With", "XMLHttpRequest"));
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(COOKIE);
        assertThat(cookie).as("refresh cookie").isNotNull();
        return new Cookie(COOKIE, cookie.getValue());
    }
}
