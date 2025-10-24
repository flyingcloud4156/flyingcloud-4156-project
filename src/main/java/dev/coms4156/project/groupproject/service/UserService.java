package dev.coms4156.project.groupproject.service;

import com.baomidou.mybatisplus.extension.service.IService;
import dev.coms4156.project.groupproject.dto.LoginRequest;
import dev.coms4156.project.groupproject.dto.RegisterRequest;
import dev.coms4156.project.groupproject.dto.TokenPair;
import dev.coms4156.project.groupproject.dto.UserLookupResponse;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.User;

/** Service for user-related operations. */
public interface UserService extends IService<User> {
  UserLookupResponse lookupUser(String email);

  void register(RegisterRequest req);

  TokenPair login(LoginRequest req);

  TokenPair refresh(String refreshToken);

  void logout(String refreshToken);

  UserView currentUser();

  UserView getProfile(Long userId);
}
