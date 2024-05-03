import React, { Component } from "react";
import { Container, Row, Col, TabContent, TabPane, Nav, NavItem, NavLink } from "reactstrap";
import classnames from 'classnames';

// Import Image
import Img1 from "../assets/images/faq/img-1.png";

class FAQ extends Component {
  constructor(props) {
    super(props);
    this.state = {
      activeTab: "v-pills-work",
    };
    this.toggleTab = this.toggleTab.bind(this);
  }
  toggleTab(tab) {
    if (this.state.activeTab !== tab) {
      this.setState({
        activeTab: tab,
      });
    }
  }
  render() {
    return (
      <React.Fragment>
        <section className="section" id="faq">
          <Container>
            <Row className="justify-content-center">
              <Col lg={7}>
                <div className="text-center mb-5">
                  <h2 className="">Frequently Asked Questions</h2>
                  <p className="text-muted">Ut enim ad minima veniam quis nostrum exercitationem ullam corporis suscipit laboriosam nisi commodi consequatur.</p>
                </div>
              </Col>
            </Row>
            <div className="faq-content">
              <Row className="align-items-center">
                <Col lg={6} className="order-2 order-lg-1">
                  <TabContent id="v-pills-tabContent" activeTab={this.state.activeTab}>
                    <TabPane tabId="v-pills-work" className="fade show">
                      <img src={Img1} alt="" className="img-fluid d-block mx-auto" />
                    </TabPane>
                  </TabContent>
                </Col>
                <Col lg={5} className="offset-lg-1 order-1 order-lg-2">
                  <Nav className="flex-column" pills id="v-pills-tab" role="tablist" aria-orientation="vertical">
                    <NavItem>
                      <NavLink href="#" className={classnames({ active: this.state.activeTab === 'v-pills-work' },"rounded" )} onClick={() => { this.toggleTab('v-pills-work'); }} id="v-pills-work-tab">
                        <h4 className="text-dark f-18">Question 1</h4>
                        <p className="text-muted f-15">At solmen necessi a uniform grammatica pronunciation sommun plu paroles ma quande.</p>
                      </NavLink>
                    </NavItem>
                    <NavItem>
                      <NavLink href="#" className={classnames({ active: this.state.activeTab === 'v-pills-marketing' },"rounded" )}  onClick={() => { this.toggleTab('v-pills-marketing'); }} id="v-pills-work-tab">
                        <h4 className="text-dark f-18">Question 2</h4>
                        <p className="text-muted f-15">At solmen necessi a uniform grammatica pronunciation sommun plu paroles ma quande.</p>
                      </NavLink>
                    </NavItem>
                    <NavItem>
                      <NavLink href="#" className={classnames({ active: this.state.activeTab === 'v-pills-feedback' },"rounded" )} onClick={() => { this.toggleTab('v-pills-feedback'); }} id="v-pills-work-tab">
                        <h4 className="text-dark f-18">Question 3</h4>
                        <p className="text-muted f-15">At solmen necessi a uniform grammatica pronunciation sommun plu paroles ma quande.</p>
                      </NavLink>
                    </NavItem>
                  </Nav>
                </Col>
              </Row>
            </div>
          </Container>
        </section>
      </React.Fragment>
    );
  }
}

export default FAQ;