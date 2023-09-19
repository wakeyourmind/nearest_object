create table if not exists way_record
(
    distance Float64,
    object String
) engine = MergeTree()
ORDER BY distance;