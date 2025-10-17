package dev.coms4156.project.groupproject.service;

import com.baomidou.mybatisplus.extension.service.IService;
import dev.coms4156.project.groupproject.dto.*;
import dev.coms4156.project.groupproject.entity.User;

public interface UserService extends IService<User> {
  UserLookupResponse lookupUser(String email);

  void register(RegisterRequest req);

  TokenPair login(LoginRequest req);

  TokenPair refresh(String refreshToken);

  void logout(String refreshToken);

  UserView currentUser();

  UserView getProfile(Long userId);

  void updateMe(UpdateProfileRequest req);

  void changePassword(ChangePasswordRequest req);
}
