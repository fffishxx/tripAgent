export interface UserSummary {
  userId: string
  phone: string
  nickname: string
  avatarUrl?: string | null
}

export interface UserProfile extends UserSummary {
  email?: string | null
  status?: number
  lastLoginTime?: string | null
  createTime?: string
}

export interface LoginPayload {
  phone: string
  password: string
}

export interface LoginResult {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: UserSummary
}

export interface RegisterPayload {
  phone: string
  password: string
  nickname?: string
  email?: string
}

export interface RegisterResult {
  userId: string
  phone: string
  nickname: string
}

export interface StoredAuthSession {
  token: string
  user: UserSummary
  expiresAt: number | null
}
