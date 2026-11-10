 
import React from 'react'

interface LayoutProps {
    children: React.ReactNode
}

export const EmptyLayout = ({children} : LayoutProps) => {
  return (
    <div>{children}</div>
  )
}
