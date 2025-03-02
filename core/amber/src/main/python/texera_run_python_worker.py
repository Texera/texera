import sys

from loguru import logger

from core.python_worker import PythonWorker
from core.storage.storage_config import StorageConfig


def init_loguru_logger(stream_log_level) -> None:
    """
    initialize the loguru's logger with the given configurations
    :param stream_log_level: level to be output to stdout/stderr
    :return:
    """

    # loguru has default configuration which includes stderr as the handler. In order to
    # change the configuration, the easiest way is to remove any existing handlers and
    # re-configure them.
    logger.remove()

    # set up stream handler, which outputs to stderr
    logger.add(sys.stderr, level=stream_log_level)


if __name__ == "__main__":
    (
        _,
        worker_id,
        output_port,
        logger_level,
        r_path,
        iceberg_postgres_catalog_uri_without_scheme,
        iceberg_postgres_catalog_username,
        iceberg_postgres_catalog_password,
        iceberg_table_namespace,
        iceberg_file_storage_directory_path,
        iceberg_table_commit_batch_size,
    ) = sys.argv
    init_loguru_logger(logger_level)
    StorageConfig.initialize(
        iceberg_postgres_catalog_uri_without_scheme,
        iceberg_postgres_catalog_username,
        iceberg_postgres_catalog_password,
        iceberg_table_namespace,
        iceberg_file_storage_directory_path,
        iceberg_table_commit_batch_size,
    )

    # Setting R_HOME environment variable for R-UDF usage
    if r_path:
        import os

        os.environ["R_HOME"] = r_path

    PythonWorker(
        worker_id=worker_id, host="localhost", output_port=int(output_port)
    ).run()
