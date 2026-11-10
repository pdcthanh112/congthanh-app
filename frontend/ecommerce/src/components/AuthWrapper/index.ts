'use client'

import React, { useEffect } from "react"
import { useAppDispatch } from "@/redux/store"
import { useWishlistStore } from "@/store/wishlistStore"
import { useSession } from "next-auth/react"
import { fetchUserData } from "@/app/action/sync-data"

export default function AuthWrapper({ children }: {children: React.ReactElement}) {
    const { data: session, status } = useSession()
    const dispatch = useAppDispatch()
    const setWishlist = useWishlistStore(state => state.setItems)
  
    useEffect(() => {
      const syncUserData = async () => {
        if (status === 'authenticated') {
          try {
            // // Sử dụng Promise.all để gọi đồng thời các requests
            // const [cartData, wishlistData] = await Promise.all([
            //   fetchUserCart(),
            //   fetchUserWishlist()
            // ])
            const {data} = await fetchUserData()
            // Lưu vào Redux
            dispatch(setCartItems(data?.cart))

             // Lưu vào Zustand
          setWishlist(data?.wishlist)
          
        } catch (error) {
          console.error('Failed to sync user data:', error)
        }
      }
    }

    // Chỉ gọi khi status thay đổi từ loading -> authenticated
    if (status === 'authenticated') {
      syncUserData()
    }
  }, [status, dispatch, setWishlist])

  return children
}