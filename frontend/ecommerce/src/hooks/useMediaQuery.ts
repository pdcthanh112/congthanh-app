import { useEffect, useState } from "react";

export const useMediaQuery = (query: string) => {
    const [matches, setMatches] = useState(false);
  
    useEffect(() => {
      const mediaQuery = window.matchMedia(query);
  
      const handleMediaQueryChange = (event: any) => {
        setMatches(event.matches);
      };
  
      mediaQuery.addListener(handleMediaQueryChange);
      setMatches(mediaQuery.matches);
  
      return () => {
        mediaQuery.removeListener(handleMediaQueryChange);
      };
    }, [query]);
  
    return matches;
  };

//   const isMobile = useMediaQuery("(max-width: 768px)");

//   return <div>{isMobile ? <p>Mobile View</p> : <p>Desktop View</p>}</div>;