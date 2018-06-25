#!/usr/bin/python

"""
The following program is to be ran on a RASPBERRY PI 3 MODEL B and used in conjunction with AWS.
It connects to S3 and SNS using a file named "aws_sdk.properties" with the following layout where "***" is the respected information. :

[Data]
aws_access_key_id=***
aws_secret_access_key=***
bucket=***
topic_arn=***

After the device is connected, it is able to utilize the USB camera to take and send multiple pictures. 
"""
import os
import socket
import ssl
from time import sleep
from random import uniform
import subprocess
import RPi.GPIO as GPIO
import time
import sys
import boto
import boto.s3.connection
import boto.sns
from boto.s3.key import Key
import ConfigParser

__author__ = "Tamara Wertheim and Nathan Robinson"
__copyright__ = "Copyright 2018, Cloud Computing Project"
__version__ = "1.0.0"

# Set numbering mode
GPIO.setmode(GPIO.BCM)

# Setup the output pin
GPIO.setup(23, GPIO.IN, pull_up_down=GPIO.PUD_UP)

# Read the aws_sdk.properties file 
config = ConfigParser.RawConfigParser()
config.read('aws_sdk.properties')

# Retrieve variables from the aws_sdk.properties file 
AWS_ACCESS_KEY_ID = config.get('Data','aws_access_key_id')
AWS_SECRET_ACCESS_KEY = config.get('Data','aws_secret_access_key')
S3BUCKET = config.get('Data','bucket')
TOPIC_ARN = config.get('Data','topic_arn')

# Connect to S3
s3_connection = boto.connect_s3(AWS_ACCESS_KEY_ID,
        AWS_SECRET_ACCESS_KEY)

bucket = s3_connection.get_bucket(S3BUCKET)

# Connect to SNS and send message verifying connection
sns_connection = boto.connect_sns(AWS_ACCESS_KEY_ID,
        AWS_SECRET_ACCESS_KEY)
publish = sns_connection.publish(topic=TOPIC_ARN,message='\nDevice is connected')

i=0

try:

    while True:
        
        # Set input state
        input_state = GPIO.input(23)

        # If input is detected, then take picture and upload to S3
        # Picture has format pic(i).jpg for continuous uploading
        if input_state == 0:

            # The USB camera is called
            subprocess.call('fswebcam -d /dev/video0 -r 1024x768 -S0 pic%s.jpg' % i, shell=True)
            print('PIC CAPTURED')

            # The picture is saved to the device and named pic(i).jpg
            pic = '/home/pi/pic%s.jpg' % i
            data = open(pic, 'rb')
            
            print ('Uploading to Amazon S3 bucket')

            def percent_cb(complete, total):
                sys.stdout.write('.')
                sys.stdout.flush()
            
            # The picture is put into S3 with the pic(i).jpg
            k = bucket.new_key('pic%s.jpg' % i)
            k.set_contents_from_filename('pic%s.jpg' % i)

            # Variable goes up to avoid overwrite of previous pictures
            i=i+1

            print ('Done')

            # Pause program for .2 seconds
            time.sleep(0.2)

# Stops the program if a keyboard interrupt is present
except KeyboardInterrupt:
    
    # Cleans up the ports
    GPIO.cleanup()