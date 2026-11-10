import Image from 'next/image';
import AppLogo from '@/assets/images/app-logo.png';
import FacebookIcon from '@/assets/icons/facebook-icon.svg';
import TwitterIcon from '@/assets/icons/twitter-icon.svg';
import LinkedInIcon from '@/assets/icons/linkedin-icon.svg';
import InstagramIcon from '@/assets/icons/instagram-icon.svg';
import YoutubeIcon from '@/assets/icons/youtube-icon.svg';
import GitHubIcon from '@/assets/icons/github-icon.svg';
import LocationIcon from '@/assets/icons/location-icon.svg';
import PhoneIcon from '@/assets/icons/phone-icon.svg';
import WebsiteIcon from '@/assets/icons/website-icon.svg';
import EmailIcon from '@/assets/icons/email-icon.svg';
import Link from 'next/link';

const AppFooter = (): React.ReactElement => {
  const currentYear = new Date().getFullYear();

  const FieldSocialNetwork = ({ url, image, alt }: { url: string; image: any; alt: string }) => (
    <Link href={url}>
      <Image src={image} alt={alt} style={{ width: '2rem', height: 'auto' }} className="mx-2" />
    </Link>
  );

  const FieldInfomation = ({ image, title }: { image: any; title: string }) => (
    <div className="flex items-center py-2">
      <Image src={image} alt="Location icon" style={{ width: '1.2rem', height: '1.2rem' }} />
      <span className="ml-2">{title}</span>
    </div>
  );

  return (
    <footer className="grid grid-cols-12 px-1 md:px-3 lg:px-5 py-4 md:py-2 lg:py-3 bg-[rgba(20,177,231,.1)]">
      <div className="col-span-12 md:col-span-5">
        <div className="px-2 md:px-8 lg:px-16">
          DreamHigh is a Cong Thanh&apos;s personal project, including an e-commerce site, official website, employee
          management page and administrator&apos;s site. Specific description at &nbsp;
          <Link href="https://github.com/pdcthanh112/CongThanhApp.git" className="underline">
            GitHub
          </Link>
        </div>
        <div className="flex justify-center">
          <Image
            src={AppLogo}
            alt="App logo"
            style={{ width: '30%', height: 'auto' }}
            className="flex justify-center"
          />
        </div>
        <div className="flex justify-center">Connect to me</div>
        <div className="w-full inline-flex justify-center">
          <FieldSocialNetwork url="https://www.facebook.com/pdcthanh112/" image={FacebookIcon} alt="Facebook" />
          <FieldSocialNetwork url="https://www.linkedin.com/in/pdcthanh112dev/" image={LinkedInIcon} alt="LinkedIn" />
          <FieldSocialNetwork url="https://www.linkedin.com/in/pdcthanh112dev/" image={TwitterIcon} alt="Twitter" />
          <FieldSocialNetwork url="https://www.linkedin.com/in/pdcthanh112dev/" image={InstagramIcon} alt="Instagram" />
          <FieldSocialNetwork url="https://www.linkedin.com/in/pdcthanh112dev/" image={YoutubeIcon} alt="Youtube" />
          <FieldSocialNetwork url="https://www.linkedin.com/in/pdcthanh112dev/" image={GitHubIcon} alt="GitHub" />
        </div>
        <div className="flex justify-center">&copy;{currentYear} by pdcthanh</div>
      </div>
      <div className="col-span-6 md:col-span-2">
        <ul className="text-[#626d79]">
          <li>Java Spring framework</li>
          <li>NodeJS Express</li>
          <li>Python Django</li>
          <li>PostgreSQL</li>
          <li>MySQL</li>
          <li>MongoDB</li>
          <li>Redis</li>
          <li>Elasticsearch</li>
        </ul>
      </div>
      <div className="col-span-6 md:col-span-2">
        <ul className="text-[#626d79]">
          <li>React</li>
          <li>NextJS</li>
          <li>Angular</li>
          <li>Flutter</li>
          <li>React Native</li>
          <li>AWS</li>
          <li>Firebase</li>
        </ul>
      </div>
      <div className="col-span-12 md:col-span-3">
        <FieldInfomation image={LocationIcon} title="Silicon Valley, San Francisco, California, US" />
        <FieldInfomation image={PhoneIcon} title="(+84) 382-722-849" />
        <FieldInfomation image={WebsiteIcon} title="congthanhapp.com" />
        <FieldInfomation image={EmailIcon} title="pdcthanh112.dev@gmail.com" />
      </div>
    </footer>
  );
};

export default AppFooter;
