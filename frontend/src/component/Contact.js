import React, { Component } from "react";

//import icon
import { Col, Container, Form, FormGroup, Input, Label, Row , Button} from "reactstrap";


// Import Background Image
import ContactImg from "../assets/images/contact-img.png";

export default class Contact extends Component {
  render() {
    return (
      <React.Fragment>
        <section className="section bg-light" id="contact">
          <Container>
            <Row className="justify-content-center">
              <Col lg={7}>
                <div className="text-center mb-5">
                  <h2 className="">Contact Us</h2>
                  <p className="text-muted">Ut enim ad minima veniam quis nostrum exercitationem ullam corporis suscipit laboriosam nisi commodi consequatur.</p>
                </div>
              </Col>
            </Row>
            <Row className="align-items-center justify-content-center">
              <Col lg={6} md={10}>
                <div className="mb-5 mb-lg-0">
                  <div className="text-center">
                    <img src={ContactImg} alt="" className="img-fluid d-block w-75 mx-auto" />
                    <h4 className="mt-4">Are You Facing Any Problem..?</h4>
                    <p className="text-muted mb-4">It va esser tam simplic quam Occidental in fact it va esser Occidental angles quam un skeptic cambridge es.</p>
                  </div>
                </div>
              </Col>
              <Col lg={5} className="offset-lg-1">
                <h4 className="line-height-1_4 mb-4">Get in Touch</h4>
                <div className="custom-form mt-4 mt-lg-0">
                  <div id="message"></div>
                  <Form method="post" name="contact-form" id="contact-form">
                    <Row>
                      <Col md={6}>
                        <FormGroup className="app-label">
                          <Label for="name" className="form-label">Name</Label>
                          <Input name="name" id="name" type="text" className="form-control" placeholder="Enter your name .." />
                        </FormGroup>
                      </Col>
                      <Col md={6}>
                        <FormGroup className="app-label">
                          <Label for="lastname" className="form-label">Email address</Label>
                          <Input name="email" id="email" type="email" className="form-control" placeholder="Enter your email .." />
                        </FormGroup>
                      </Col>
                      <Col md={12}>
                        <FormGroup className="app-label">
                          <Label for="email" className="form-label">Subject</Label>
                          <Input name="text" id="text" type="text" className="form-control" placeholder="Enter Subject .." />
                        </FormGroup>
                      </Col>
                      <Col md={12}>
                        <FormGroup className="app-label">
                          <Label for="comments" className="form-label">Message</Label>
                          <Input name="comments" id="comments" type="textarea" rows="4" className="form-control" placeholder="Enter Message. . ." />
                        </FormGroup>
                      </Col>
                    </Row>
                    <Row>
                      <Col lg={12}>
                        <Button color="primary" id="submit" name="send" className="btn btn-primary">Send Message</Button>
                        <div id="simple-msg"></div>
                      </Col>
                    </Row>
                  </Form>
                </div>
              </Col>
            </Row>
          </Container>
        </section>
      </React.Fragment>
    );
  }
}
