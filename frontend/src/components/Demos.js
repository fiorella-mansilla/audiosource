import React, { Component } from "react";
import { Col, Container, Row } from "reactstrap";

export default class Demos extends Component {
  render() {
    return (
      <React.Fragment>
        <section className="section bg-light" id="demos">
          <Container>
            <Row className="justify-content-center">
              <Col lg={7}>
                <div className="text-center mb-5">
                  <h2 className="">Demos</h2>
                  <p className="text-muted">Ut enim ad minima veniam quis nostrum exercitationem ullam corporis suscipit laboriosam nisi commodi consequatur.</p>
                </div>
              </Col>
            </Row>
            <Row>
              <Col lg={4}>
                <div className="card service-box text-center p-4">
                    <div className="service-icon-bg mx-auto avatar-xxl p-4"></div>
                  <h4 className="service-title mt-4 mb-3 f-18">Sample</h4>
                  <p className="service-subtitle mb-4 f-15">Omnicos directe al desirabilite de une nov lingua franca a refusa continuar payar custosi traductores.</p>
                </div>
              </Col>
              <Col lg={4}>
                <div className="card service-box text-center p-4">
                  <div className="service-icon-bg mx-auto avatar-xxl p-4"></div>
                  <h4 className="service-title mt-4 mb-3 f-18">Instrumental</h4>
                  <p className="service-subtitle mb-4 f-15">Omnicos directe al desirabilite de une nov lingua franca a refusa continuar payar custosi traductores.</p>
                </div>
              </Col>
              <Col lg={4}>
                <div className="card service-box text-center p-4">
                  <div className="service-icon-bg mx-auto avatar-xxl p-4"></div>
                  <h4 className="service-title mt-4 mb-3 f-18">Vocal</h4>
                  <p className="service-subtitle mb-4 f-15">Omnicos directe al desirabilite de une nov lingua franca a refusa continuar payar custosi traductores.</p>
                </div>
              </Col>
            </Row>
          </Container>
        </section>
      </React.Fragment>
    );
  }
}
