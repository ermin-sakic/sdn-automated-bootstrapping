#!/bin/bash
maxF=$1

./connect_sw.sh sw_1 sw_5 veth1 veth2
./connect_sw.sh sw_1 sw_7 veth3 veth4
./connect_sw.sh sw_1 sw_9 veth5 veth6
./connect_sw.sh sw_1 sw_11 veth7 veth8

./connect_sw.sh sw_2 sw_5 veth9 veth10
./connect_sw.sh sw_2 sw_7 veth11 veth12
./connect_sw.sh sw_2 sw_9 veth13 veth14
./connect_sw.sh sw_2 sw_11 veth15 veth16

./connect_sw.sh sw_3 sw_6 veth17 veth125
./connect_sw.sh sw_3 sw_8 veth18 veth19
./connect_sw.sh sw_3 sw_10 veth20 veth21
./connect_sw.sh sw_3 sw_12 veth22 veth23

./connect_sw.sh sw_4 sw_6 veth24 veth25
./connect_sw.sh sw_4 sw_8 veth26 veth27
./connect_sw.sh sw_4 sw_10 veth28 veth29
./connect_sw.sh sw_4 sw_12 veth30 veth31

./connect_sw.sh sw_5 sw_13 veth31 veth32
./connect_sw.sh sw_5 sw_14 veth126 veth127
./connect_sw.sh sw_6 sw_13 veth33 veth34
./connect_sw.sh sw_6 sw_14 veth35 veth36

./connect_sw.sh sw_7 sw_15 veth37 veth38
./connect_sw.sh sw_7 sw_16 veth39 veth40
./connect_sw.sh sw_8 sw_15 veth41 veth42
./connect_sw.sh sw_8 sw_16 veth43 veth44

./connect_sw.sh sw_9 sw_17 veth85 veth86
./connect_sw.sh sw_9 sw_18 veth87 veth88
./connect_sw.sh sw_10 sw_17 veth89 veth90
./connect_sw.sh sw_10 sw_18 veth91 veth92

./connect_sw.sh sw_11 sw_19 veth45 veth46
./connect_sw.sh sw_11 sw_20 veth47 veth48
./connect_sw.sh sw_12 sw_19 veth49 veth50
./connect_sw.sh sw_12 sw_20 veth51 veth52

# Attach clients - two per leaf switch
./connect_sw.sh cl_1 sw_13 veth500 veth501
./connect_sw.sh cl_2 sw_13 veth502 veth503

./connect_sw.sh cl_3 sw_14 veth504 veth505
./connect_sw.sh cl_4 sw_14 veth506 veth507

./connect_sw.sh cl_5 sw_15 veth508 veth509
./connect_sw.sh cl_6 sw_15 veth510 veth511

./connect_sw.sh cl_7 sw_16 veth512 veth513
./connect_sw.sh cl_8 sw_16 veth514 veth515

./connect_sw.sh cl_9 sw_17 veth516 veth517
./connect_sw.sh cl_10 sw_17 veth518 veth519

./connect_sw.sh cl_11 sw_18 veth520 veth521
./connect_sw.sh cl_12 sw_18 veth522 veth523

./connect_sw.sh cl_13 sw_19 veth524 veth525
./connect_sw.sh cl_14 sw_19 veth526 veth527

./connect_sw.sh cl_15 sw_20 veth528 veth529
./connect_sw.sh cl_16 sw_20 veth530 veth531

if [ "$maxF" == 1 ]
then
 ./connect_sw.sh co_1 sw_13 veth200 veth201
 ./connect_sw.sh co_2 sw_15 veth202 veth203
 ./connect_sw.sh co_3 sw_17 veth204 veth205
 ./connect_sw.sh co_4 sw_19 veth206 veth207
 
 ./connect_sw.sh shf_1 sw_16 veth208 veth209
elif [ "$maxF" == 2 ]
then
 ./connect_sw.sh co_1 sw_13 veth200 veth201
 ./connect_sw.sh co_2 sw_15 veth202 veth203
 ./connect_sw.sh co_3 sw_17 veth204 veth205
 ./connect_sw.sh co_4 sw_19 veth206 veth207
 ./connect_sw.sh co_5 sw_14 veth208 veth209
 ./connect_sw.sh co_6 sw_18 veth210 veth211
 ./connect_sw.sh co_7 sw_20 veth212 veth213
 
 ./connect_sw.sh shf_1 sw_16 veth214 veth215
elif [ "$maxF" == 3 ]
then
 ./connect_sw.sh co_1 sw_13 veth200 veth201
 ./connect_sw.sh co_2 sw_15 veth202 veth203
 ./connect_sw.sh co_3 sw_17 veth204 veth205
 ./connect_sw.sh co_4 sw_19 veth206 veth207
 ./connect_sw.sh co_5 sw_14 veth208 veth209
 ./connect_sw.sh co_6 sw_16 veth210 veth211
 ./connect_sw.sh co_7 sw_18 veth212 veth213
 ./connect_sw.sh co_8 sw_20 veth214 veth215
 ./connect_sw.sh co_9 sw_13 veth216 veth217
 ./connect_sw.sh co_10 sw_15 veth218 veth219
 
 ./connect_sw.sh shf_1 sw_16 veth220 veth221
elif [ "$maxF" == 4 ]
then
 ./connect_sw.sh co_1 sw_13 veth200 veth201
 ./connect_sw.sh co_2 sw_15 veth202 veth203
 ./connect_sw.sh co_3 sw_17 veth204 veth205
 ./connect_sw.sh co_4 sw_19 veth206 veth207
 ./connect_sw.sh co_5 sw_14 veth208 veth209
 ./connect_sw.sh co_6 sw_16 veth210 veth211
 ./connect_sw.sh co_7 sw_18 veth212 veth213
 ./connect_sw.sh co_8 sw_20 veth214 veth215
 ./connect_sw.sh co_9 sw_13 veth216 veth217
 ./connect_sw.sh co_10 sw_15 veth218 veth219
 ./connect_sw.sh co_11 sw_17 veth220 veth221
 ./connect_sw.sh co_12 sw_19 veth222 veth223
 ./connect_sw.sh co_13 sw_14 veth224 veth225

 ./connect_sw.sh shf_1 sw_16 veth226 veth227
elif [ "$maxF" == 5 ]
then
 ./connect_sw.sh co_1 sw_13 veth200 veth201
 ./connect_sw.sh co_2 sw_15 veth202 veth203
 ./connect_sw.sh co_3 sw_17 veth204 veth205
 ./connect_sw.sh co_4 sw_19 veth206 veth207
 ./connect_sw.sh co_5 sw_14 veth208 veth209
 ./connect_sw.sh co_6 sw_16 veth210 veth211
 ./connect_sw.sh co_7 sw_18 veth212 veth213
 ./connect_sw.sh co_8 sw_20 veth214 veth215
 ./connect_sw.sh co_9 sw_13 veth216 veth217
 ./connect_sw.sh co_10 sw_15 veth218 veth219
 ./connect_sw.sh co_11 sw_17 veth220 veth221
 ./connect_sw.sh co_12 sw_19 veth222 veth223
 ./connect_sw.sh co_13 sw_14 veth224 veth225
 ./connect_sw.sh co_14 sw_16 veth226 veth227
 ./connect_sw.sh co_15 sw_18 veth228 veth229
 ./connect_sw.sh co_16 sw_20 veth230 veth231

 ./connect_sw.sh shf_1 sw_16 veth232 veth233
fi
