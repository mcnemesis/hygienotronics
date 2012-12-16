#!/usr/bin/env python

import web
import serial

urls = ("/.*", "command")
app = web.application(urls, globals())

ARD = '/dev/ttyACM0'

class command:
    def GET(self):
        param = web.input(c="")
        s = serial.Serial(ARD)#,9600)
        if param.c != '':
            s.write(param.c)
        #s.close()
        return 'OK'

if __name__ == "__main__":
    app.run()
