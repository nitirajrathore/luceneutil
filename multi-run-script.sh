
MY_HOME="/home/nitirar"
LUCENE_PATH="$MY_HOME/mywork/lucene"
DOC_VEC_PATH="$LUCENE_PATH/benchmarks/data/enwiki-20120502-lines-1k-100d.vec"
INDEX_PATH="$LUCENE_PATH/benchmarks/indices/vector_index"
SIM_FOLDER="$MY_HOME/sims/disconnected"
EXP_FOLDER="$MY_HOME/sims/disconnected/exp9"
#for i in $(seq 0 10000 1000000)
for i in $(echo 20000)
do
  EXP_PATH="$EXP_FOLDER/$i"
  mkdir -p $EXP_PATH
  rm -rf "$INDEX_PATH"
  ./gradlew :src:main:run -PmainClass=knn.KnnIndexerMain --args=" -docvectorspath $DOC_VEC_PATH -indexpath $INDEX_PATH \
  -maxconn 16 -beamwidth 100 -vectorencoding FLOAT32 -similarityfunction DOT_PRODUCT -numdocs 1000000 -docstartindex $i \
   -dimension 100" > $EXP_PATH/start-$i-debug-sysout.log

  RESULT_FILE="$EXP_PATH/disconnected_result-$i.txt"
  ./gradlew :src:main:run -PmainClass=knn.CheckHNSWConnectedness --args="$INDEX_PATH knn" > $RESULT_FILE


  result=$(cat "$RESULT_FILE" | grep "Unreachable Nodes" | awk -F '\t' '{print $4}' | cut -d'=' -f2)
  overall_result=$(cat "$RESULT_FILE" | grep "Overall" | cut -f4 -d$'\t')

  mv $SIM_FOLDER/*_graphdump $EXP_PATH

  full_disconnected=$(expr $overall_result)

  if [ "$full_disconnected" != 0 ];
  then
    echo "Found overall disconnectedness for : $RESULT_FILE"
  fi

  for r in $(echo $result)
  do
     x=$(expr "$r")
     if [ "$x" != 0 ] ;
     then
      echo "Found disconnected for : $RESULT_FILE"
     fi
  done

done
