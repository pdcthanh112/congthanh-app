import { Navigate, Outlet, useLocation } from "react-router-dom"
import { useAuth } from "../context/AuthContext"
import { RouteUtils } from "../routes"

const ProtectedLayout = () => {
  const {accessToken} = useAuth()
  const location = useLocation()

if(!accessToken) {
  return <Navigate to={RouteUtils.LoginPage} state={{from: location}} replace/>
}

  return <Outlet />
}

export default ProtectedLayout