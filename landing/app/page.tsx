import Nav from "../components/Nav";
import Hero from "../components/Hero";
import Features from "../components/Features";
import Compliance from "../components/Compliance";
import Platforms from "../components/Platforms";
import Cta from "../components/Cta";
import Footer from "../components/Footer";

export default function Home() {
  return (
    <>
      <Nav />
      <main>
        <Hero />
        <Features />
        <Compliance />
        <Platforms />
        <Cta />
      </main>
      <Footer />
    </>
  );
}
