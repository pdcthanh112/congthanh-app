import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import './App.css'
import ProtectedLayout from './layout/ProtectedLayout'
import LoginPage from './pages/login/LoginPage'
import ProductManagementPage from './pages/product/list'
import { RouteUtils } from './routes'

function App() {

  const publicRoutes = [
    {path: '/', component: () => <Navigate to={RouteUtils.HomePage}/>},
    {path: RouteUtils.LoginPage, component: LoginPage}
  ]

  const protectedRoutes = [
    {path: RouteUtils.ProductManagement, component: ProductManagementPage}
  ]
  return (
    <BrowserRouter>
      <Routes>
        {publicRoutes.map(({path, component: Component}) => 
        <Route key={path} path={path} element={<Component/>}/>
        )}
        <Route element={<ProtectedLayout />}>
        {protectedRoutes.map(({path, component: Component}) => <Route key={path} path={path} element={<Component/>} />)}
        </Route>
      </Routes>
    </BrowserRouter>
  )
}

export default App
