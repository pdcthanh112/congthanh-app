import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5*6*1000,
      retry: 3,
      refetchOnWindowFocus: false,
      gcTime: 0
    }
  }
})

interface QueryProviderProps {
  children: React.ReactNode
}

export const QueryProvider: React.FC<QueryProviderProps> = ({children}) => {
return (
  <QueryClientProvider client={queryClient}>
    {children}
  </QueryClientProvider>
)
}