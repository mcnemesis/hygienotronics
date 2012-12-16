// Project 5 - LED Chase Effect (modified to give music visualization effects)
// Create array for LED pins
byte G1 = 11, R1 = 12, Y1 = 13, G2 = 10, R2 = 9, Y2 = 8;
byte ledPin[] = {G1, R1, Y1, G2, R2, Y2};
byte ledPin1[] = {G1, R1, Y1};//toilet
byte ledPin2[] = {G2, R2, Y2}; //water
int serialBufferWait = 500;//msecs
const int BUFFER_LEN = 2;//we expect strings of max len 1 + NULL char (A-Z)
char buffer[BUFFER_LEN];
int nLEDS, nLEDS1, nLEDS2;

void setup() {
  // set all pins to output
  nLEDS = sizeof(ledPin) / sizeof(byte);
  nLEDS1 = sizeof(ledPin1) / sizeof(byte);
  nLEDS2 = sizeof(ledPin2) / sizeof(byte);
  
  for (int x=0; x < nLEDS; x++) {
    pinMode(ledPin[x], OUTPUT); 
  }
  
  Serial.begin(9600);//that's e.g 9600 chars persec
  Serial.flush();//clear all existing buffers
}

void loop() {
           //turnLEDSON();
  
    if (Serial.available() > 0) {
         
      delay(serialBufferWait);
      
      String serialVal = parseSerial();
      
      visualizeValue(serialVal);
      
      Serial.flush(); //just ignore this data
    }
}

String parseSerial() {
 int nSerial = Serial.available();//how many chars?
 if( nSerial > BUFFER_LEN) {
    nSerial = BUFFER_LEN; 
 }
 
 int i = 0;
 while(nSerial--) {
   buffer[i++] = Serial.read();
 }
 
 Serial.write(buffer);
 
 char val[BUFFER_LEN];
 
 strncpy(val,buffer,BUFFER_LEN);
 
 
 for(int i=0;i<BUFFER_LEN;i++) 
   buffer[i] = '\0';
  
  Serial.flush(); 
  
   Serial.write(val);
  
  return val;
}

void turnLEDSON() {
  for (int x=0; x < nLEDS; x++) {
    digitalWrite(ledPin[x], HIGH);
  }
}


void visualizeValue(String state) {
  
  //THE STATES
  
  //Toilet Monitor:
  //A: ON (g)
  //a: off (-all)
  //B: Listening (g,y,-r)
  //C: Trigger (g,r,-y)
  
  //Water Monitor:
  //P: ON (g)
  //p: off (-all)
  //Q: Listening (g,y,-r)
  //R: Trigger (g,r,-y)
  
  //for toilet monitor...
  if(state == "A") {
    turnOffLEDS1();
    turnONLED(G1);
  }
  
  if(state == "a") {
    turnOffLEDS1();
  }
  
  if(state == "B") {
    turnONLED(G1);
    turnONLED(Y1);
    turnOFFLED(R1);
  }
  
  if(state == "C") {
    turnONLED(G1);
    turnONLED(R1);
    turnOFFLED(Y1);
  }
  
  //for water monitor...
  if(state == "P") {
     turnOffLEDS2();
    turnONLED(G2);
  }
  
  if(state == "p") {
    turnOffLEDS2();
  }
  
  if(state == "Q") {
    turnONLED(G2);
    turnONLED(Y2);
    turnOFFLED(R2);
  }
  
  if(state == "R") {
    turnONLED(G2);
    turnONLED(R2);
    turnOFFLED(Y2);
  }
  
}

void turnOFFLEDS() {
  turnOffLEDS1();
  turnOffLEDS2();
}

void turnONLED(byte pin) {
  digitalWrite(pin,HIGH);
}

void turnOFFLED(byte pin) {
  digitalWrite(pin,LOW);
}

void turnOffLEDS1() {
  // turn off all LED's
  for (int x=0; x < nLEDS1; x++) {
    digitalWrite(ledPin1[x], LOW);
  }
}

void turnOffLEDS2() {
  // turn off all LED's
  for (int x=0; x < nLEDS2; x++) {
    digitalWrite(ledPin2[x], LOW);
  }
}
