import axios from "axios"
import { API_UTILS } from "../constants/api-utils"
import type { ILoginPayload } from "../types/payload"

export const AuthService = {
  Login: async (payload: ILoginPayload) => {
    return await axios.post(API_UTILS.API_AUTH.LOGIN, payload)
  }
}