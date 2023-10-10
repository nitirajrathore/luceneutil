
MY_HOME="/home/nitirar"
LUCENE_PATH="$MY_HOME/mywork/lucene"
DOC_VEC_PATH="$LUCENE_PATH/benchmarks/data/enwiki-20120502-lines-1k-100d.vec"
INDEX_PATH="$LUCENE_PATH/benchmarks/indices/vector_index"
QUERY_PATH="$LUCENE_PATH/benchmarks/luceneutil/tasks/vector-task-100d.vec"
SIM_FOLDER="$MY_HOME/sims/disconnected"
EXP_FOLDER="$MY_HOME/sims/disconnected/exp10/"
NDOCS=1000000
NITER=1000
MAX_CONN=16
BEAM_WIDTH=100
DIM=100
TOPK=10
#for i in $(seq 0 10000 1000000)
for i in $(echo 0)
do
  EXP_PATH="$EXP_FOLDER"
  mkdir -p $EXP_PATH
  rm -rf "$INDEX_PATH"
  mkdir "$INDEX_PATH"
  echo ./gradlew :src:main:run -PmainClass=knn.KnnGraphTester --args="-fanout 0 -indexPath $INDEX_PATH -ndoc $NDOCS -dim $DIM -topK $TOPK -niter $NITER -reindex \
          -docs $DOC_VEC_PATH -encoding float32 -metric angular -search $QUERY_PATH -maxConn $MAX_CONN -beamWidthIndex $BEAM_WIDTH"

  ./gradlew :src:main:run -PmainClass=knn.KnnGraphTester --args="-fanout 0 -indexPath $INDEX_PATH -ndoc $NDOCS -dim $DIM -topK $TOPK -niter $NITER -reindex \
            -docs $DOC_VEC_PATH -encoding float32 -metric angular -search $QUERY_PATH -maxConn $MAX_CONN -beamWidthIndex $BEAM_WIDTH"
done
