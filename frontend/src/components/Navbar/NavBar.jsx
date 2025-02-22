import React, { Component } from "react";
import {
  Navbar,
  Nav,
  NavbarToggler,
  NavItem,
  NavLink,
  Container,
  Collapse
} from "reactstrap";
import ScrollspyNav from "./Scrollspy";

class NavbarPage extends Component {
  constructor(props) {
    super(props);
    this.state = {
      isOpenMenu: false,
    };
  }
  render() {
    let targetId = this.props.navItems.map((item) => {
      return item.idnm;
    });
    return (
      <React.Fragment>
        <Container>
          <Navbar
            expand="lg"
            fixed={this.props.top === true ? "top" : ""}
            className={this.props.navClass + " fixed-top navbar-custom sticky sticky-dark align-items-center "}
            id="navbar"
            container
          >
            <NavbarToggler onClick={this.toggle}>
              <span className="ti-menu"></span>
            </NavbarToggler>
            <Collapse
              id="navbarCollapse"
              isOpen={this.state.isOpenMenu}
              className=" navbar-collapse "
            >
              <ScrollspyNav
                scrollTargetIds={targetId}
                scrollDuration="800"
                headerBackground="true"
                activeNavClass="active"
                className="navbar-collapse"
              >
                <Nav className="navbar-nav navbar-center" id="navbar-navlist">
                  {this.props.navItems.map((item, key) => (
                    <NavItem
                      key={key}
                      className={item.navheading === "Home" ? "active" : ""}
                    >
                      <NavLink
                        className={item.navheading === "Home" ? "active" : ""}
                        href={"#" + item.idnm}
                      >
                        {item.navheading}
                      </NavLink>
                    </NavItem>
                  ))}
                </Nav>
              </ScrollspyNav>
            </Collapse>
          </Navbar>
        </Container>
      </React.Fragment>
    );
  }
}
// }
export default NavbarPage;