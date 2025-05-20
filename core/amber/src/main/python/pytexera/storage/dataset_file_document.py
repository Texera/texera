# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

import os
import io
import requests
import urllib.parse
import pandas as pd

from core.models import TableLike


class DatasetFileDocument:
    def __init__(self, file_path: str):
        """
        Parses the file path into dataset metadata.

        :param file_path:
           Expected format - "/ownerEmail/datasetName/versionName/fileRelativePath"
           Example: "/bob@texera.com/twitterDataset/v1/california/irvine/tw1.csv"
        """
        parts = file_path.strip("/").split("/")
        if len(parts) < 4:
            raise ValueError(
                "Invalid file path format. "
                "Expected: /ownerEmail/datasetName/versionName/fileRelativePath"
            )

        self.owner_email = parts[0]
        self.dataset_name = parts[1]
        self.version_name = parts[2]
        self.file_relative_path = "/".join(parts[3:])

        self.jwt_token = os.getenv("USER_JWT_TOKEN")
        self.presign_endpoint = os.getenv("FILE_SERVICE_GET_PRESIGNED_URL_ENDPOINT")

        if not self.jwt_token:
            raise ValueError(
                "JWT token is required but not set in environment variables."
            )
        if not self.presign_endpoint:
            self.presign_endpoint = "http://localhost:9092/api/dataset/presign-download"

    def get_presigned_url(self) -> str:
        """
        Requests a presigned URL from the API.

        :return: The presigned URL as a string.
        :raises: RuntimeError if the request fails.
        """
        headers = {"Authorization": f"Bearer {self.jwt_token}"}
        encoded_file_path = urllib.parse.quote(
            f"/{self.owner_email}"
            f"/{self.dataset_name}"
            f"/{self.version_name}"
            f"/{self.file_relative_path}"
        )

        params = {"filePath": encoded_file_path}

        response = requests.get(self.presign_endpoint, headers=headers, params=params)

        if response.status_code != 200:
            raise RuntimeError(
                f"Failed to get presigned URL: "
                f"{response.status_code} {response.text}"
            )

        return response.json().get("presignedUrl")

    def read_file(self) -> io.BytesIO:
        """
        Reads the file content from the presigned URL.

        :return: A file-like object.
        :raises: RuntimeError if the retrieval fails.
        """
        presigned_url = self.get_presigned_url()
        response = requests.get(presigned_url)

        if response.status_code != 200:
            raise RuntimeError(
                f"Failed to retrieve file content: "
                f"{response.status_code} {response.text}"
            )

        return io.BytesIO(response.content)

    def read_as_table(self, **pandas_kwargs) -> "TableLike":
        """
        Download the file and materialise it as a pandas DataFrame.

        Parameters
        ----------
        **pandas_kwargs :
            Extra keyword arguments forwarded to the relevant
            ``pandas.read_*`` function (e.g., ``read_csv``).

        Returns
        -------
        TableLike  (currently a pandas.DataFrame)
            The tabular representation of the file’s contents.

        Notes
        -----
        This is a *hacky* helper—intended only for local Python-side
        experimentation.  For production use, push the logic into a
        proper service layer.
        """

        # Pull the bytes from object storage
        file_bytes = self.read_file()

        # Infer file format from the extension
        ext = self.file_relative_path.rsplit(".", 1)[-1].lower()

        if ext in {"csv", "tsv", "txt"}:
            # default separator if caller didn't pass one
            if "sep" not in pandas_kwargs:
                pandas_kwargs["sep"] = "," if ext == "csv" else "\t"
            df = pd.read_csv(file_bytes, **pandas_kwargs)
        elif ext in {"json", "ndjson"}:
            df = pd.read_json(file_bytes, lines=(ext == "ndjson"), **pandas_kwargs)
        elif ext in {"parquet"}:
            df = pd.read_parquet(file_bytes, **pandas_kwargs)
        else:
            raise ValueError(f"Unsupported file type: .{ext}")

        # Return as pandas.DataFrame (which is accepted as TableLike)
        return df
