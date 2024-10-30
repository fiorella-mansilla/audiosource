import React, { Component } from "react";
import { Col, Container, Row } from "reactstrap";

// Import Background Image
import Img1 from "../assets/images/about/img-1.png";
import Img2 from "../assets/images/about/img-2.png";

export default class About extends Component {
  render() {
    return (
      <React.Fragment>
        <section className="section" id="about">
          <Container>
            <Row className="justify-content-center">
              <Col lg={7}>
                <div className="text-center mb-5">
                  <h2 className="">How it works</h2>
                  <p className="text-muted">Once you upload a song, the Demucs model will separate the original track based on the separation type and the output format that you have selected. </p>                </div>
              </Col>
            </Row>
            <Row>
              <Col lg={4}>
                <h2 className="fw-normal line-height-1_4 mb-4">AI-Powered Music <span className="fw-medium">Separator</span></h2>
                <p className="text-muted mb-4">Any song can be processed, but the processing time may vary depending on the complexity of the song.</p>
              </Col>
              <Col lg={4}>
                <div className="card border-0">
                  <div className="bg-soft-primary about-img rounded">
                  <img src={Img1} alt="" className="img-fluid d-block mx-auto" />
                  </div>
                  <div className="mt-3">
                    <p className="text-uppercase text-muted mb-2 f-13">Karaoke</p>
                    <h4 className="f-18">Vocal Remover</h4>
                    <p className="text-muted">Remove vocals from any song by creating a karaoke. You will get two tracks - the accompaniment without vocals and the acapella version (isolated vocals).</p>
                  </div>
                </div>
              </Col>
              <Col lg={4}>
                <div className="card border-0">
                  <div className="bg-soft-info about-img rounded">
                    <img src={Img2} alt="" className="img-fluid d-block mx-auto" />
                  </div>
                  <div className="mt-3">
                    <p className="text-uppercase text-muted mb-2 f-13">Stems</p>
                    <h4 className="f-18">Stem Splitter</h4>
                    <p className="text-muted">Get the multitracks out of any song by separating your music into individual stems - isolated vocals, drums, bass, and the rest of the accompaniment.</p>
                    </div>
                </div>
              </Col>
            </Row>
          </Container>
        </section>
      </React.Fragment>
    );
  }
}
