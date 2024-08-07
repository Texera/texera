package edu.uci.ics.texera.workflow.operators.sklearn

class SklearnKNNOpDesc extends SklearnMLOpDesc {
  override def getImportStatements = "from sklearn.neighbors import KNeighborsClassifier"
  override def getUserFriendlyModelName = "K-nearest Neighbors"
}
