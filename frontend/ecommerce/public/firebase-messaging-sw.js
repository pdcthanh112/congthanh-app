importScripts('https://www.gstatic.com/firebasejs/9.0.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/9.0.0/firebase-messaging-compat.js');

const firebaseConfig = {
  apiKey: "AIzaSyCt_DDyVvOOKBvfux9mLa5icTISruKznCA",
  authDomain: "congthanh-project.firebaseapp.com",
  projectId: "congthanh-project",
  storageBucket: "congthanh-project.appspot.com",
  messagingSenderId: "1085433653419",
  appId: "1:1085433653419:web:4e998619aed7b8f8142fee",
  measurementId: "G-Q2XZTSFQQP"
};

firebase.initializeApp(firebaseConfig);
const messaging = firebase.messaging();

// Xử lý thông báo background (khi ứng dụng không mở)
messaging.onBackgroundMessage((payload) => {
  // const notificationTitle = payload.notification?.title || 'New Notification';
  // const notificationOptions = {
  //   body: payload.notification?.body || 'You have a new notification!',
  //   icon: '/favicon.ico'
  // };
  // self.registration.showNotification(notificationTitle, notificationOptions);
});