import http from '@/utils/request'
import type {
  LoginPayload,
  LoginResult,
  RegisterPayload,
  RegisterResult,
  UserProfile,
} from '@/types/auth'

/**
 * 认证接口封装。
 * 登录成功后由 auth store 保存 USER_JWT，并随业务请求发送 Authorization 头。
 */
export function login(payload: LoginPayload): Promise<LoginResult> {
  return http.post<LoginResult>('/v1/auth/login', payload)
}

export function register(payload: RegisterPayload): Promise<RegisterResult> {
  return http.post<RegisterResult>('/v1/auth/register', payload)
}

export function fetchCurrentUser(): Promise<UserProfile> {
  return http.get<UserProfile>('/v1/users/me')
}

export function updateCurrentUser(
  payload: Partial<Pick<UserProfile, 'nickname' | 'avatarUrl' | 'email'>>,
): Promise<UserProfile> {
  return http.put<UserProfile>('/v1/users/me', payload)
}
