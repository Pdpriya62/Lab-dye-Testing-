# Pitch: Multi-Sensor IoT Rig for Lab-Dip Testing

Spoken script, about 2 to 3 minutes (roughly 430 words). Team 11, RV University.

---

## What it is (20 s)

Good morning. We are Team 11, and our project is a low-cost IoT rig for lab-dip testing.

Before a textile mill dyes hundreds of kilos of fabric, it dyes a small sample first. That sample is called a lab dip, and it goes to the buyer for approval. Our rig measures the colour of that sample and, at the same moment, records the conditions it was dyed and measured under.

## The problem (35 s)

When a lab dip is rejected, people usually blame the recipe. But the recipe is not the only thing that changes the shade. If the dye bath runs two degrees cold, the fabric takes up less dye. If light leaks into the viewing box, the sample looks lighter. Humidity matters too.

Most labs never write these conditions down next to the colour reading. So when a dip fails, nobody knows why, and every extra round costs dye, water and at least a day. A spectrophotometer measures colour well, but it is expensive and records nothing about the process.

## How it works (60 s)

The fabric goes under this box. Inside are two colour sensors. A TCS34725 colour sensor reads the fabric with its own light. An ESP32 camera takes a photo, which our software corrects against white paper and checks for even dyeing, cell by cell.

A waterproof probe sits in the dye bath, which today is this glass of ink and water. On the breadboard we also measure room temperature, humidity and light.

An ESP32 reads every sensor every three seconds, converts the colour to CIE Lab, and works out the colour difference, Delta E, against the target shade. It all goes out as one MQTT message. Our Android app shows it live: pass or fail, the camera result, and an alert if the bath drifts more than two degrees. From the app we capture the target, pause while we swap fabric, and set each sample's ID.

On the laptop, a logger saves every reading, and our analysis ranks which condition most likely caused a colour difference.

## Limitations (35 s)

This is not a spectrophotometer. The colour sensor is a low-cost part, calibrated against white paper, not certified colour standards.

Our analysis results come from a simulated dataset of sixty lab dips, not real dyeing yet. They show the method works, not how strong each effect is in a real lab.

The demo uses a public MQTT broker without encryption, and samples are identified by typed IDs rather than QR codes.

## Close (10 s)

Our next step is real dyeing trials checked against a spectrophotometer. The idea stays the same: when a lab dip fails, it should come with its likely cause. Thank you.
