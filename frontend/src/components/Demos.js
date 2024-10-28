import React, { Component } from "react";
import { Col, Container, Row, Button } from "reactstrap";
import { FaPlay, FaPause } from "react-icons/fa";

// Import Audio files for Demo
import originalSampleAudio from "../assets/audios/original-sample.wav";
import instrumentalAudio from "../assets/audios/instrumental.wav";
import vocalsAudio from "../assets/audios/vocals.wav";

export default class Demos extends Component {
  constructor(props) {
    super(props);
    this.audioRefs = {
      original: new Audio(originalSampleAudio),
      instrumental: new Audio(instrumentalAudio),
      vocals: new Audio(vocalsAudio),
    };
    this.state = {
      playingAudio: null,
    };
  }

  componentWillUnmount() {
    // Stop any playing audio on unmount
    Object.values(this.audioRefs).forEach((audio) => {
      audio.pause();
    });
  }

  handlePlayPause = (audioKey) => {
    const { playingAudio } = this.state;
    const audio = this.audioRefs[audioKey];

    // Stop any currently playing audio if it's not the selected one
    if (playingAudio && playingAudio !== audio) {
      playingAudio.pause();
      playingAudio.currentTime = 0;
    }

    // Toggle play/pause for the selected audio
    if (audio === playingAudio) {
      audio.pause();
      this.setState({ playingAudio: null });
    } else {
      audio.play();
      this.setState({ playingAudio: audio });
    }
  };
  
  render() {
    const { playingAudio } = this.state;

    // Data array with each card's unique details
    const audioCards = [
      {
        key: "original",
        title: "Original Sample",
        paragraph: "This is the original version of the track with all components.",
      },
      {
        key: "instrumental",
        title: "Instrumental",
        paragraph: "Listen to the karaoke version, highlighting the music without vocals.",
      },
      {
        key: "vocals",
        title: "Vocals",
        paragraph: "Enjoy the vocal version, bringing only the vocals to the forefront.",
      },
    ];

    return (
      <React.Fragment>
        <section className="section bg-light" id="demos">
          <Container>
            <Row className="justify-content-center">
              <Col lg={7}>
                <div className="text-center mb-5">
                  <h2 className="">Demos</h2>
                </div>
              </Col>
            </Row>
            <Row>
              {audioCards.map(({ key, title, paragraph }) => (
                <Col lg={4} key={key}>
                  <div className="card service-box text-center p-4">
                    <div className="service-icon-bg mx-auto avatar-xxl p-4">
                      <Button
                        color="primary"
                        onClick={() => this.handlePlayPause(key)}
                      >
                        {playingAudio === this.audioRefs[key] ? (
                          <FaPause />
                        ) : (
                          <FaPlay />
                        )}
                      </Button>
                    </div>
                    <h4 className="service-title mt-4 mb-3 f-18">
                      {title}
                    </h4>
                    <p className="service-subtitle mb-4 f-15">
                      {paragraph}
                    </p>
                  </div>
                </Col>
              ))}
            </Row>
          </Container>
        </section>
      </React.Fragment>
    );
  }
}
