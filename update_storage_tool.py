import json
import argparse
from datetime import datetime, timezone, date

# tool to remove files from storage after a certain date (and thus to email group of files added after that date)
# usage example  python3 update_storage_tool.py -f "storage.json" -r "2020-10-24"

parser = argparse.ArgumentParser()
parser.add_argument(
    "-f", help="Provide JSON storage file to parse", required=True)
parser.add_argument(
    "-r", help="Remove all entries later than date, provided as argument in 1900-1-1 format", required=True)
args = parser.parse_args()


def main():

    def get_date_from_unix_epoch(timestamp):
        dt = datetime.fromtimestamp(int(timestamp)/1000, tz=timezone.utc)
        return dt

    def get_timestamp_from_date(date_str):
        date_obj = date.fromisoformat(date_str)
        # tm_year=1985, tm_mon=10, tm_mday=5
        date_tuple = date.timetuple(date_obj)
        timestamp = int(datetime(
            date_tuple.tm_year, date_tuple.tm_mon, date_tuple.tm_mday, 0, 0).timestamp())

        return timestamp

    def get_files_list(file_path):

        file_obj = open(file_path)

        serialized = json.load(file_obj)

        return serialized

    def get_files_later_than(files_list, timestamp):
        later_than_dict = dict()

        for this_key in files_list.keys():
            last_modified = int(files_list[this_key]["lastModified"])/1000
            if last_modified > timestamp:
                later_than_dict[this_key] = files_list[this_key]
        return later_than_dict

    def remove_later_files(destination, later_files):
        # return new json with the files later than specified date removed
        old_files_dict = get_files_list(destination)
        for file_path in later_files:
            if file_path in old_files_dict.keys():
                del old_files_dict[file_path]
        write_file(destination, json.dumps(old_files_dict))
        return

    def write_file(destination, data_str):
        # Write json to destination file
        with open(destination, "w") as outfile:
            outfile.write(data_str)
        # Close opened file
        outfile.close()
        return

    if args.f and args.r:
        read_write_file = args.f
        remove_after_date = args.r
        timestamp = get_timestamp_from_date(remove_after_date)
        files_list = get_files_list(read_write_file)
        later_than = get_files_later_than(files_list, timestamp)
        remove_later_files(read_write_file, later_than)
    else:
        print("One or more required arguments not provided")


if __name__ == "__main__":
    main()
