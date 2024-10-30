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
                        <h4 className="text-dark f-18">How will I receive my audio files once they are separated?</h4>
                        <p className="text-muted f-15">You will receive the audio files via email. Just enter your email address in the form and wait for the confirmation that the audio has been successfully uploaded. There's no need to stay on the page while the processing occurs, as you will receive the results in your email.</p>
                      </NavLink>
                    </NavItem>
                    <NavItem>
                      <NavLink href="#" className={classnames({ active: this.state.activeTab === 'v-pills-work' },"rounded" )}  onClick={() => { this.toggleTab('v-pills-work'); }} id="v-pills-work-tab">
                        <h4 className="text-dark f-18">Which music source separation model are you using?</h4>
                        <p className="text-muted f-15">We utilize the Demucs model developed and trained by Facebook Research. This model is built on a U-Net convolutional architecture, drawing inspiration from Wave-U-Net.</p>
                      </NavLink>
                    </NavItem>
                    <NavItem>
                      <NavLink href="#" className={classnames({ active: this.state.activeTab === 'v-pills-work' },"rounded" )} onClick={() => { this.toggleTab('v-pills-work'); }} id="v-pills-work-tab">
                        <h4 className="text-dark f-18">Can I submit multiple audio files at once?</h4>
                        <p className="text-muted f-15">Due to processing time and load constraints, you can only submit one audio file at a time.</p>
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