"""
This is to create dataset that does not contain text so that it can be released.
One can recover the NYT dataset by using SalienceDatasetTextOnlyWriter.
"""

import sys
import json
import gzip

input_path = sys.argv[1]
output_path = sys.argv[2]

count = 0
with gzip.open(output_path, 'w') as f:
    with gzip.open(input_path, 'r') as f_in:
        for line in f_in:
            doc_info = json.loads(line)
            doc_info['bodyText'] = ''
            doc_info['abstract'] = ''

            line_text = json.dumps(doc_info) + '\n'
            f.write(line_text.encode('utf-8'))

            count += 1
            sys.stdout.write('Write %d files\r' % count)

    sys.stdout.write('\nDone writing.')
