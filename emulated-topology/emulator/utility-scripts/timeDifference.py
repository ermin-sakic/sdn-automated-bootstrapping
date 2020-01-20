import datetime

t1 = datetime.datetime(2018,6,21,17,4,9,630000)
t2 = datetime.datetime(2018,6,21,17,4,44,174000)

dt1 = t2 - t1

print(dt1)

t1 = datetime.datetime(2018,6,21,17,6,58,591000)
t2 = datetime.datetime(2018,6,21,17,7,36,223000)

dt2 = t2 - t1

print(dt2)

print((dt1+dt2)/2)
