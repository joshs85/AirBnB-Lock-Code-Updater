#from icalendar import Calendar
import urllib2
from ics import Calendar
from datetime import datetime
import arrow
import re
import requests
import os
iCalURL = os.environ['iCalURL']
SmartThingsUrl = os.environ['SmartThingsUrl']
STAccessToken = os.environ['STAccessToken']
CheckinHour = int(os.environ['CheckinHour'])
CheckOutHour = int(os.environ['CheckOutHour'])

#Set these env variables in AWS
#iCalURL = "https://www.airbnb.com/calendar/ical/xxxxxxxxxxxxxxx"
#SmartThingsUrl = "https://graph-na02-useast1.api.smartthings.com:443/api/smartapps/installations/xxxxxxxx/lock/code"
#STAccessToken = "xxxxxxx"
#CheckinHour = 15
#CheckOutHour = 11

def get_phone(event):
    phoneregex = 'PHONE:\s+([0-9 \+\-\(\)]+)'
    parse = re.compile(phoneregex,re.MULTILINE)
    return parse.findall(event)[0]

def set_lock_code(code, eventTitle):
    params = {
        'access_token': STAccessToken,
        'code': code,
        'title': eventTitle
    }
    headers = {
        'Content-Type': 'application/json'
    }
    return requests.post(url=SmartThingsUrl, params=params, headers=headers)

def get_lock_code():
    params = {
        "access_token": STAccessToken
    }
    headers = {
        'Content-Type': 'application/json'
    }
    return requests.get(url=SmartThingsUrl, params=params, headers=headers)

def delete_lock_code():
    params = {
        "access_token": STAccessToken
    }
    headers = {
        'Content-Type': 'application/json'
    }
    return requests.delete(url=SmartThingsUrl, params=params, headers=headers)


def handler(event, context):
    try:
        opener = urllib2.build_opener()
        opener.addheaders = [('User-agent', 'Mozilla/5.0')]
        ics = opener.open(iCalURL).read().decode('iso-8859-1')
        c = Calendar(ics)
        lock_code_updated = False
        delete_code = False
        print "Server Local Time: {0}".format(arrow.now())
        print "Server UTC Time: {0}".format(arrow.utcnow())        
        now = arrow.utcnow().to('US/Eastern')
        print "Server UTC Converted to Eastern time: {0}".format(now)
        print "Starting compare of events"
        for event in c.events:
            eventTitle = event.name
            if not lock_code_updated and eventTitle != "Not available":
                begin = event.begin.replace(hour=CheckinHour, minute=0, second=0, tzinfo='US/Eastern')
                end = event.end.replace(hour=CheckOutHour, minute=30, second=0, tzinfo='US/Eastern')
                description = event.description
                print "Comparing Title:{} - Now:{} to EventBegin:{} with EventEnd:{}".format(eventTitle, now, begin, end)
                if now.date() == begin.date():
                    phone = get_phone(description)
                    lock_code = phone[-4:]
                    print "Setting lock code to {0} for {1}".format(lock_code, eventTitle)
                    set_lock_code(lock_code, eventTitle)
                    lock_code_updated = True
                elif now > end and end.date() == now.date():
                    delete_code=True
        if delete_code and not lock_code_updated:
            print "Guest's stay ended today.  Deleting Lock Code"
            delete_lock_code()
        return {'Lock Code Updated': lock_code_updated}
    except Exception as ex:
        print ex
        return False
