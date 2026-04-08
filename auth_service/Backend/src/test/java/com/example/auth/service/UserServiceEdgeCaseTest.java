package com.example.auth.service;

import com.example.auth.dto.UserProfileDto;
import com.example.auth.model.User;
import com.example.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class UserServiceEdgeCaseTest {

    @Mock private UserRepository userRepository;
    @InjectMocks private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setName("Test User");
        testUser.setEmail("test@aegis.com");
        testUser.setMobileNo("9876543210");
        testUser.setPanNo("ABCDE1234F");
        testUser.setDob(LocalDate.of(2000, 5, 15));
    }

    @Test
    @DisplayName("User not found → throws UsernameNotFoundException")
    void userNotFound_Throws() {
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userService.getProfileByEmail("ghost@test.com"));
    }

    @Test
    @DisplayName("All fields populated → full profile returned")
    void allFieldsPopulated_FullProfile() {
        when(userRepository.findByEmail("test@aegis.com")).thenReturn(Optional.of(testUser));

        UserProfileDto profile = userService.getProfileByEmail("test@aegis.com");

        assertEquals("Test User", profile.getName());
        assertEquals("test@aegis.com", profile.getEmail());
        assertEquals("9876543210", profile.getMobileNo());
        assertEquals("ABCDE1234F", profile.getPanNo());
        assertNotNull(profile.getDob());
    }

    @Test
    @DisplayName("Null name → returns '-' for name")
    void nullName_ReturnsDash() {
        testUser.setName(null);
        when(userRepository.findByEmail("test@aegis.com")).thenReturn(Optional.of(testUser));

        UserProfileDto profile = userService.getProfileByEmail("test@aegis.com");

        assertEquals("-", profile.getName());
    }

    @Test
    @DisplayName("Blank name → returns '-' for name")
    void blankName_ReturnsDash() {
        testUser.setName("   ");
        when(userRepository.findByEmail("test@aegis.com")).thenReturn(Optional.of(testUser));

        UserProfileDto profile = userService.getProfileByEmail("test@aegis.com");

        assertEquals("-", profile.getName());
    }

    @Test
    @DisplayName("Null mobileNo → returns '-'")
    void nullMobile_ReturnsDash() {
        testUser.setMobileNo(null);
        when(userRepository.findByEmail("test@aegis.com")).thenReturn(Optional.of(testUser));

        UserProfileDto profile = userService.getProfileByEmail("test@aegis.com");

        assertEquals("-", profile.getMobileNo());
    }

    @Test
    @DisplayName("Null panNo → returns '-'")
    void nullPan_ReturnsDash() {
        testUser.setPanNo(null);
        when(userRepository.findByEmail("test@aegis.com")).thenReturn(Optional.of(testUser));

        UserProfileDto profile = userService.getProfileByEmail("test@aegis.com");

        assertEquals("-", profile.getPanNo());
    }

    @Test
    @DisplayName("Null dob → dob returns '-'")
    void nullDob_ReturnsDash() {
        testUser.setDob(null);
        when(userRepository.findByEmail("test@aegis.com")).thenReturn(Optional.of(testUser));

        UserProfileDto profile = userService.getProfileByEmail("test@aegis.com");

        assertEquals("-", profile.getDob());
    }

    @Test
    @DisplayName("Null createdAt → createdAt returns null")
    void nullCreatedAt_ReturnsNull() {
        when(userRepository.findByEmail("test@aegis.com")).thenReturn(Optional.of(testUser));

        UserProfileDto profile = userService.getProfileByEmail("test@aegis.com");

        assertNull(profile.getCreatedAt());
    }

    @Test
    @DisplayName("Null email → returns '-' for email")
    void nullEmail_ReturnsDash() {
        testUser.setEmail(null);
        when(userRepository.findByEmail("test@aegis.com")).thenReturn(Optional.of(testUser));

        UserProfileDto profile = userService.getProfileByEmail("test@aegis.com");

        assertEquals("-", profile.getEmail());
    }
}
