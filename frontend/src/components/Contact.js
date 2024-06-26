import React, { Component } from "react";

//import icon
import { Col, Container, Form, FormGroup, Input, Label, Row , Button} from "reactstrap";

export default class Contact extends Component {
  render() {
    return (
      <React.Fragment>
        <section className="section bg-light" id="contact">
          <Container>
            <Row className="justify-content-center">
              <Col lg={7}>
                <div className="text-center mb-4">
                  <h2 className="">Contact Us</h2>
                </div>
              </Col>
            </Row>
            <Row className="align-items-center justify-content-center">
              <Col lg={5} className="offset-lg-1">
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
