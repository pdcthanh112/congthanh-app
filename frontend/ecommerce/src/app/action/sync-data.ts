'use server';

import { getServerSession } from 'next-auth';
import { authOptions } from '../api/auth/[...nextauth]/route';

export async function fetchUserData() {
  const session = await getServerSession(authOptions);

  if (!session) {
    return { success: false, error: 'Unauthorized' };
  }

  try {
    const userSettingsResponse = await fetch(`${process.env.API_URL}/user-settings/${session.user?.email}`);
    const userSettings = await userSettingsResponse.json();

    const cartResponse = await fetch(`${process.env.API_URL}/cart/${session.user?.email}`);
    const cart = await cartResponse.json();

    const wishlistResponse = await fetch(`${process.env.API_URL}/wishlist/${session.user?.email}`);
    const wishlist = await wishlistResponse.json();

    return {
      success: true,
      data: {
        userSettings,
        cart,
        wishlist,
      },
    };
  } catch (error) {
    return { success: false, error: error.message };
  }
}
