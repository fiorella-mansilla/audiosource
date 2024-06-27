import React, { Component } from "react";
import { Link } from "react-router-dom";
import { Container, Row, Col } from "reactstrap";
import FooterLink from "../Footer/Footer_link";

// Footer Image
import footer_image from "../../assets/images/hero-1-bg-img.png";

class Footer extends Component {
  constructor(props) {
    super(props);
    this.state = {
      links: [
        {
          id: 1,
          title: "Customer",
          child: [
            { title: "Works", link: "/" },
            { title: "Strategy", link: "/" },
            { title: "Releases", link: "/" },
          ],
        },
        {
          id: 2,
          title: "Product",
          child: [
            { title: "Tranding", link: "/" },
            { title: "Popular", link: "/" },
            { title: "Customers", link: "/" },
          ],
        },
        {
          id: 3,
          title: "Learn More",
          child: [
            { title: "Developers", link: "/" },
            { title: "Support", link: "/" },
            { title: "Customer Service", link: "/" },
          ],
        },
      ],
    };
  }

  render() {
    return (
      <React.Fragment>
        {/* Footer Start */}
        <footer
          className="footer"
          style={{ backgroundImage: "url(" + footer_image + ")" }}
        >
          <Container>
            <Row>
              <Col lg={4}>
                <div className="mb-5">
                  <p className="text-white-50 my-4">Cras ultricies mi eu turpis sit hendrerit fringilla vestibulum ante ipsum primis in faucibus ultrices posuere cubilia.</p>
                </div>
              </Col>
              <Col lg={7} className="offset-lg-1">
                <Row>
                  {/* Render Footer Link */}
                  {this.state.links.map((item, key) => (
                    <Col md={4} key={key}>  
                    <h4 className="text-white f-22 fw-normal mb-3">
                          {item.title}
                        </h4>
                        <ul className="list-unstyled footer-sub-menu">
                          {item.child.map((linkItem, key) => (
                            <li key={key}>
                              <Link className="footer-link" to={linkItem.link}>
                                {linkItem.title}
                              </Link>
                            </li>
                          ))}
                        </ul>
                    </Col>
                  ))}
                </Row>
              </Col>
            </Row>
            {/* Render Footer Link End */}
            <FooterLink />
          </Container>
        </footer>
        {/* Footer End */}
      </React.Fragment>
    );
  }
}

export default Footer;
