package com.rideshare.user;

import com.rideshare.security.AuthenticatedUser;
import com.rideshare.user.dto.ChangePasswordRequest;
import com.rideshare.user.dto.UpdateProfileRequest;
import com.rideshare.user.dto.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me")
@Tag(name = "Profile", description = "The signed-in student's own profile")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "Get my profile")
    public UserProfileResponse me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return userService.getProfile(currentUser.id());
    }

    @PutMapping
    @Operation(summary = "Update my name and phone number")
    public UserProfileResponse update(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                      @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(currentUser.id(), request);
    }

    @PutMapping("/password")
    @Operation(summary = "Change my password", description = "Returns 204. 401 INVALID_CREDENTIALS if the current password is wrong.")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthenticatedUser currentUser,
                                               @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(currentUser.id(), request);
        return ResponseEntity.noContent().build();
    }
}
