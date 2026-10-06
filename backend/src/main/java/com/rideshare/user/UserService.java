package com.rideshare.user;

import com.rideshare.common.exception.AuthenticationFailedException;
import com.rideshare.common.exception.ErrorCode;
import com.rideshare.common.exception.ForbiddenOperationException;
import com.rideshare.common.exception.InvalidRequestException;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.user.dto.ChangePasswordRequest;
import com.rideshare.user.dto.UpdateProfileRequest;
import com.rideshare.user.dto.UserProfileResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /** Loads a user that must exist and be active (used by every write path). */
    @Transactional(readOnly = true)
    public User getActiveUser(Long userId) {
        User user = getUser(userId);
        if (!user.isActive()) {
            throw new ForbiddenOperationException(ErrorCode.ACCOUNT_DISABLED, "This account has been deactivated");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User not found"));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return userMapper.toProfile(getUser(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getActiveUser(userId);
        user.setName(request.name().trim());
        user.setPhoneNumber(normalizePhone(request.phoneNumber()));
        return userMapper.toProfile(user);
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = getActiveUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new AuthenticationFailedException(ErrorCode.INVALID_CREDENTIALS, "Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new InvalidRequestException(ErrorCode.INVALID_PASSWORD, "New password must differ from the current one");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    static String normalizePhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone.trim();
    }
}
