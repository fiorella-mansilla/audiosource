import React, { Component, Suspense } from "react";

// Importing Section
const Navbar = React.lazy(() => import("../../components/Navbar/NavBar"));

const Section = React.lazy(() => import("./Section"));
const About = React.lazy(() => import("../../components/About"));
const Demos = React.lazy(() => import("../../components/Demos"));
const FAQ = React.lazy(() => import("../../components/FAQ"));
const Contact = React.lazy(() => import("../../components/Contact"));
const Footer = React.lazy(() => import("../../components/Footer/Footer"));

// import { Spinner } from "reactstrap";

class Layout extends Component {
  constructor(props) {
    super(props);
    this.state = {
      navItems: [
        { id: 1, idnm: "home", navheading: "Home" },
        { id: 2, idnm: "about", navheading: "About" },
        { id: 3, idnm: "demos", navheading: "Demos" },
        { id: 4, idnm: "faq", navheading: "FAQ" },
        { id: 5, idnm: "contact", navheading: "Contact Us" },
      ],
      pos: document.documentElement.scrollTop,
      imglight: false,
      navClass: "",
    };
  }

  componentDidMount() {
    window.addEventListener("scroll", this.scrollNavigation, true);
  }

  componentWillUnmount() {
    window.removeEventListener("scroll", this.scrollNavigation, true);
  }

  scrollNavigation = () => {
    var scrollup = document.documentElement.scrollTop;
    if (scrollup > this.state.pos) {
      this.setState({ navClass: "nav-sticky", imglight: false });
    } else {
      this.setState({ navClass: "", imglight: true });
    }
  };

  //set preloader div
  PreLoader = () => {
    return (
      <div id="preloader">
        <div id="status">
          <div className="spinner">
            <div className="bounce1"></div>
            <div className="bounce2"></div>
            <div className="bounce3"></div>
          </div>
        </div>
      </div>
    );
  };

  render() {
    return (
      <React.Fragment>
        <Suspense fallback={this.PreLoader()}>
          {/* Importing Navbar */}
          <Navbar
            navItems={this.state.navItems}
            navClass={this.state.navClass}
            imglight={this.state.imglight}
          />

          {/* Importing Section */}
          <Section />

          {/* Importing About */}
          <About />

           {/* Importing Service */}
           <Demos />

          {/* Importing Feature */}
          <FAQ />

          {/* Importing Contact Us */}
          <Contact />

          {/* Importing Footer */}
          <Footer />
        </Suspense>
      </React.Fragment>
    );
  }
}
export default Layout;
