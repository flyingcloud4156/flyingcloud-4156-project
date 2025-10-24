package dev.coms4156.project.groupproject.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import dev.coms4156.project.groupproject.dto.LoginRequest;
import dev.coms4156.project.groupproject.dto.RegisterRequest;
import dev.coms4156.project.groupproject.dto.TokenPair;
import dev.coms4156.project.groupproject.dto.UserLookupResponse;
import dev.coms4156.project.groupproject.dto.UserView;
import dev.coms4156.project.groupproject.entity.User;
import dev.coms4156.project.groupproject.mapper.UserMapper;
import dev.coms4156.project.groupproject.service.UserService;
import dev.coms4156.project.groupproject.utils.CurrentUserContext;
import dev.coms4156.project.groupproject.utils.Jsons;
import dev.coms4156.project.groupproject.utils.PasswordUtil;
import dev.coms4156.project.groupproject.utils.RedisKeys;
import dev.coms4156.project.groupproject.utils.SystemConstants;
import dev.coms4156.project.groupproject.utils.TokenUtil;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Implementation of the UserService interface. */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

  private final StringRedisTemplate redis;

  public UserServiceImpl(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public UserLookupResponse lookupUser(String email) {
    if (!StringUtils.hasText(email)) {
      throw new RuntimeException("VALIDATION_FAILED");
    }

    LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
    queryWrapper.eq(User::getEmail, email);

    User user = getOne(queryWrapper, false);

    if (user == null) {
      throw new RuntimeException("USER_NOT_FOUND");
    }

    return new UserLookupResponse(user.getId(), user.getName());
  }

  @Override
  @Transactional
  public void register(RegisterRequest req) {
    // uniqueness: email
    long cnt = count(new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()));
    if (cnt > 0) {
      throw new RuntimeException("Email already registered");
    }
    User u = new User();
    u.setEmail(req.getEmail());
    u.setName(
        StringUtils.hasText(req.getName())
            ? req.getName()
            : SystemConstants.DEFAULT_NICK_PREFIX + TokenUtil.shortUuid());
    u.setPasswordHash(PasswordUtil.hashPassword(req.getPassword()));
    u.setTimezone("America/New_York");
    u.setMainCurrency("USD");
    u.setPhone(null);
    save(u);
  }

  @Override
  public TokenPair login(LoginRequest req) {
    User user = getOne(new LambdaQueryWrapper<User>().eq(User::getEmail, req.getEmail()), false);
    if (user == null) {
      throw new RuntimeException("User not found");
    }
    if (!PasswordUtil.verifyPassword(req.getPassword(), user.getPasswordHash())) {
      throw new RuntimeException("Wrong credentials");
    }
    return issueTokens(user);
  }

  @Override
  public TokenPair refresh(String refreshToken) {
    String json = redis.opsForValue().get(RedisKeys.refreshTokenKey(refreshToken));
    if (json == null) {
      throw new RuntimeException("Invalid or expired refresh token");
    }
    UserView uv = Jsons.fromJson(json, UserView.class);
    // rotate refresh token
    redis.delete(RedisKeys.refreshTokenKey(refreshToken));
    User user = getById(uv.getId());
    if (user == null) {
      throw new RuntimeException("User not found");
    }
    return issueTokens(user);
  }

  @Override
  public void logout(String refreshToken) {

    if (!StringUtils.hasText(refreshToken)) {

      return;
    }

    // delete refresh; access tokens will expire on their own

    redis.delete(RedisKeys.refreshTokenKey(refreshToken));

    CurrentUserContext.clear();
  }

  @Override
  public UserView currentUser() {

    UserView uv = CurrentUserContext.get();

    if (uv == null) {

      throw new RuntimeException("Not logged in");
    }

    return uv;
  }

  @Override
  public UserView getProfile(Long userId) {

    User user = getById(userId);

    if (user == null) {

      return null;
    }

    return new UserView(user.getId(), user.getName());
  }

  private TokenPair issueTokens(User user) {
    // store a compact session object
    UserView uv = new UserView(user.getId(), user.getName());
    String access = TokenUtil.randomToken();
    String refresh = TokenUtil.randomToken();
    redis
        .opsForValue()
        .set(
            RedisKeys.accessTokenKey(access),
            Jsons.toJson(uv),
            Duration.ofHours(RedisKeys.ACCESS_TOKEN_TTL_HOURS));
    redis
        .opsForValue()
        .set(
            RedisKeys.refreshTokenKey(refresh),
            Jsons.toJson(uv),
            Duration.ofDays(RedisKeys.REFRESH_TOKEN_TTL_DAYS));
    TokenPair pair = new TokenPair();
    pair.setAccessToken(access);
    pair.setRefreshToken(refresh);
    return pair;
  }
}
