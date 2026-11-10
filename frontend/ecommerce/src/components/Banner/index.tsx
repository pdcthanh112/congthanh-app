import { Carousel } from 'antd';
import Image from 'next/image';
import Banner1 from '@assets/images/banner1.jpg';
import Banner2 from '@assets/images/banner2.jpg';
import Banner3 from '@assets/images/banner3.jpg';

const contentStyle: React.CSSProperties = {
  margin: 0,
  height: '160px',
  backgroundColor: '#364d79',
  padding: 60
};

const Banner = (): React.ReactElement => {
  const carousel = [ Banner1, Banner2, Banner3];

  return (
    <Carousel autoplay infinite style={{ width: '99vw' }} >
      {carousel.map((item) => (
        <Image key={1} src={item} alt="Banner" loading="lazy" style={contentStyle} />
      ))}
    </Carousel>
  );
};

export default Banner;
