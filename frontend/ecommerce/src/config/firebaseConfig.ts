import { initializeApp } from "firebase/app";
import { getMessaging } from 'firebase/messaging';

// import { getAnalytics } from "firebase/analytics";

const firebaseConfig = {
  apiKey: "AIzaSyCt_DDyVvOOKBvfux9mLa5icTISruKznCA",
  authDomain: "congthanh-project.firebaseapp.com",
  projectId: "congthanh-project",
  storageBucket: "congthanh-project.appspot.com",
  messagingSenderId: "1085433653419",
  appId: "1:1085433653419:web:4e998619aed7b8f8142fee",
  measurementId: "G-Q2XZTSFQQP"
};

const app = initializeApp(firebaseConfig);
export const messaging = getMessaging(app);
// const analytics = getAnalytics(app);
